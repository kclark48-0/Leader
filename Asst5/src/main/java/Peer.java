import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import java.io.PrintWriter;
import org.json.*;

/**
 * This is the main class for the peer2peer program.
 * It starts a client with a username and host:port for the peer and host:port of the initial leader
 * This Peer is basically the client of the application, while the server (the one listening and waiting for requests)
 * is in a separate thread ServerThread
 * In here you should handle the user input and then send it to the server of annother peer or anything that needs to be done on the client side
 * YOU CAN MAKE ANY CHANGES YOU LIKE: this is a very basic implementation you can use to get started
 * 
 */

public class Peer {
	private String username;
	private BufferedReader bufferedReader;
	private ServerThread serverThread;

	private Set<SocketInfo> peers = new HashSet<SocketInfo>();
	private boolean leader = false;
	private boolean client = false;
	private SocketInfo leaderSocket;
	private SocketInfo clientSocket;
	private Hashtable<String, Integer> clientBalances;

	private int money;
	String clientID;
	
	public Peer(BufferedReader bufReader, String username,ServerThread serverThread, int balance){
		this.username = username;
		this.bufferedReader = bufReader;
		this.serverThread = serverThread;
		this.money = balance;
		this.clientBalances = new Hashtable<>(10);
		this.clientID = null;
	}

	public int getMoney() { return money; }

	public void setMoney(int money) { this.money = money; }

	public String getUsername(){ return this.username; }

	public void updateClientBalance(String clientID, int balance){
		clientBalances.put(clientID, balance);
	}

	public int readClientBalance(String clientID){
		return clientBalances.get(clientID);
	}

	public void setLeader(boolean leader, SocketInfo leaderSocket){
		this.leader = leader;
		this.leaderSocket = leaderSocket;
	}

	public void setClient(boolean client, SocketInfo clientSocket){
		this.client = client;
		this.clientSocket = clientSocket;
	}

	public boolean isLeader(){
		return leader;
	}

	public boolean isClient(){
		return client;
	}

	public void addPeer(SocketInfo si){
		peers.add(si);
	}
	
	// get a string of all peers that this peer knows
	public String getPeers(){
		String s = "";
		for (SocketInfo p: peers){
			s = s +  p.getHost() + ":" + p.getPort() + " ";
		}
		return s; 
	}

	/**
	 * Adds all the peers in the list to the peers list
	 * Only adds it if it is not the currect peer (self)
	 *
	 * @param list String of peers in the format "host1:port1 host2:port2"
	 */
	public void updateListenToPeers(String list) throws Exception {
		String[] peerList = list.split(" ");
		for (String p: peerList){
			String[] hostPort = p.split(":");

			// basic check to not add ourself, since then we would send every message to ourself as well (but maybe you want that, then you can remove this)
			if ((hostPort[0].equals("localhost") || hostPort[0].equals(serverThread.getHost())) && Integer.valueOf(hostPort[1]) == serverThread.getPort()){
				continue;
			}
			SocketInfo s = new SocketInfo(hostPort[0], Integer.valueOf(hostPort[1]));
			peers.add(s);
		}
	}
	
	/**
	 * Client waits for user to input can either exit or send a message
	 */
	public void askForInput() throws Exception {
		try {

			String amount = "error";
			while(true) {
				String message = bufferedReader.readLine();
				if (message.equals("exit")) {
					System.out.println("bye, see you next time");
					break;
				}else if(message.equals("balance")){
					if (this.isLeader()){
						pushMessage("{'type': 'balance', 'username': '"+ username +"'}");
					}else{
						System.out.println("Sorry, only the Leader can access that information");
					}
				}else if(message.equals("id")){
					System.out.println("Please enter your Client ID:");
					String clientID = bufferedReader.readLine();
					String idMessage = "{'type': 'id', 'username': '"+ username +"','data':'" + clientID + "'}";
					commLeader(idMessage);
				}else if(message.equals("credit")){
					System.out.println("Please enter the amount of credit you would like as an integer:");
					amount = bufferedReader.readLine();
					boolean validInput = false;
					while(!validInput){
						try{
							int i = Integer.parseInt(amount);
							validInput = true;
						}catch(NumberFormatException e){
							System.out.println("Invalid input, please enter an integer amount.");
						}
					}
					pushMessage("{'type': 'credit', 'username': '"+ username +"', 'amount': '" + amount + "'}");
				}else if(message.equals("payment")){
					System.out.println("Please enter the amount you would like to pay as an integer:");
					amount = bufferedReader.readLine();
					boolean validInput = false;
					while(!validInput){
						try{
							int i = Integer.parseInt(amount);
							validInput = true;
						}catch(NumberFormatException e){
							System.out.println("Invalid input, please enter an integer amount.");
						}
					}
					System.out.println(amount);
					commLeader("{'type': 'payment', 'username': '"+ username +"', 'amount': '" + amount + "'}");
				}else {
					commLeader("{'type': 'input', 'username': '"+ username +"','data':'" + message + "'}");
				}	
			}
			System.exit(0);
		
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

// ####### You can consider moving the two methods below into a separate class to handle communication
	// if you like (they would need to be adapted some of course)


	/**
	 * Send a message only to the leader 
	 *
	 * @param message String that peer wants to send to the leader node
	 * this might be an interesting point to check if one cannot connect that a leader election is needed
	 */
	public void commLeader(String message) {
		try {
			BufferedReader reader = null; 
				Socket socket = null;
				try {
					socket = new Socket(leaderSocket.getHost(), leaderSocket.getPort());
					reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					
				} catch (Exception c) {
					if (socket != null) {
						socket.close();
					} else {
						System.out.println("Could not connect to " + leaderSocket.getHost() + ":" + leaderSocket.getPort());
					}
					return; // returning since we cannot connect or something goes wrong the rest will not work. 
				}

				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				out.println(message);

				JSONObject json = new JSONObject(reader.readLine());
				System.out.println("     Received from server " + json);
				String list = json.getString("list");
				updateListenToPeers(list); // when we get a list of all other peers that the leader knows we update them

		} catch(Exception e) {
			e.printStackTrace();
		}
	}

/**
	 * Send a message to every peer in the peers list, if a peer cannot be reached remove it from list
	 *
	 * @param message String that peer wants to send to other peers
	 */
	public void pushMessage(String message) {
		try {
			System.out.println("     Trying to send to peers: " + peers.size());

			Set<SocketInfo> toRemove = new HashSet<SocketInfo>();
			BufferedReader reader = null; 
			int counter = 0;
			for (SocketInfo s : peers) {
				Socket socket = null;
				try {
					socket = new Socket(s.getHost(), s.getPort());
					reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				} catch (Exception c) {
					if (socket != null) {
						socket.close();
					} else {
						System.out.println("  Could not connect to " + s.getHost() + ":" + s.getPort());
						System.out.println("  Removing that socketInfo from list");
						toRemove.add(s);
						continue;
					}
					System.out.println("     Issue: " + c);
				}

				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				out.println(message);
				counter++;
				socket.close();
		     }
		    for (SocketInfo s: toRemove){
		    	peers.remove(s);
		    }

		    System.out.println("     Message was sent to " + counter + " peers");

		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main method saying hi and also starting the Server thread where other peers can subscribe to listen
	 *
	 * @param args[0] username
	 * @param args[1] port for server
	 */
	public static void main (String[] args) throws Exception {

		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
		String username = args[0];
		System.out.println("Hello " + username + " and welcome! Your port will be " + args[1]);

		int size = args.length;
		int balance = 0;
		if (size == 7) {
			balance = Integer.parseInt(args[6]);
			System.out.println("Started peer");
        } else {
            System.out.println("Expected: <name(String)> <peer(String)> <leader(String)> <client(String)> <isLeader(bool-String)> <isClient(bool-String)> <money(int-String)>");
            System.exit(0);
        }

        System.out.println(args[0] + " " + args[1]);
        ServerThread serverThread = new ServerThread(args[1]);
        Peer peer = new Peer(bufferedReader, username, serverThread, balance);

        String[] leaderHostPort = args[2].split(":");
		String[] clientHostPort = args[2].split(":");
        SocketInfo l = new SocketInfo(leaderHostPort[0], Integer.valueOf(leaderHostPort[1]));
		SocketInfo c = new SocketInfo(clientHostPort[0], Integer.valueOf(clientHostPort[1]));
        System.out.println(args[3]);

		String peerType;

        if (args[4].equals("true")){
			System.out.println("Is leader");
			peer.setClient(false, c);
			peer.setLeader(true, l);
		}else if(args[5].equals("true")) {
			System.out.println("Is client");
			peer.setClient(true, c);
			peer.setLeader(false, l);
			peerType = "client";

			peer.commLeader("{'type': 'join', 'username': '"+ username +"','ip':'" + serverThread.getHost() + "','port':'"
					+ serverThread.getPort() + "', 'balance': '" + peer.money + "', 'peerType': '" + peerType + "'}");
		}else {

			// add leader to list 
			peer.addPeer(l);
			peer.setLeader(false, l);

			peerType = "node";

			// send message to leader that we want to join
			peer.commLeader("{'type': 'join', 'username': '"+ username +"','ip':'" + serverThread.getHost() + "','port':'"
					+ serverThread.getPort() + "', 'balance': '" + peer.money + "', 'peerType': '" + peerType + "'}");

		}
		serverThread.setPeer(peer);
		serverThread.start();
		peer.askForInput();

	}

}