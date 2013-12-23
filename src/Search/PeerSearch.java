package Search;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
//Lonan Hugh Lardner
//00338834
//CS4032
//P2P Web Search System

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PeerSearch {
	long nodeID;
	Thread listen;
	TreeMap<Long, InetSocketAddress> routingTable = new TreeMap<Long, InetSocketAddress>();
	DatagramSocket udp_socket;
	HashMap<String, Boolean> pingACK = new HashMap<String, Boolean>();
	HashMap<String, Boolean> searchACK = new HashMap<String, Boolean>();
	HashMap<String, HashMap<String, Integer>> indexTable = new HashMap<String, HashMap<String, Integer>>();
	Vector<SearchResult> searchResult;
	Vector<String> indexACK;

	public PeerSearch(int ident) {
		nodeID = ident;
	}

	// initialise with a udp socket
	public void init(DatagramSocket udp_socket) {
		//nodeID = hashCode(String.valueOf(udp_socket.getLocalAddress()));
		this.udp_socket = udp_socket;
		listen = new Listening(udp_socket);
		listen.start();
		routingTable.put(
				nodeID,
				new InetSocketAddress(udp_socket.getLocalAddress(), udp_socket
						.getLocalPort()));
	}

	// returns network_id, a locally
	// generated number to identify peer network
	public long joinNetwork(InetSocketAddress bootstrap_node) {
		// nodeID = hashCode(String.valueOf(udp_socket.getLocalPort()));
		try {

			JSONObject toSend = new JSONObject();
			toSend.put("type", "JOINING_NETWORK");
			toSend.put("node_id", String.valueOf(nodeID));
			toSend.put("ip_address", InetAddress.getLocalHost().toString());

			send(toSend, bootstrap_node);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return nodeID;
	}

	// parameter is previously returned peer network identifier
	public boolean leaveNetwork(long network_id) {
		try {
			JSONObject toSend = new JSONObject();

			toSend.put("type", "LEAVING_NETWORK");
			toSend.put("node_id", nodeID);// a non-negative number of order
											// 2'^32^' identifying the leaving
											// node.
			for (Entry<Long, InetSocketAddress> entry : routingTable.entrySet()) {
				send(toSend, entry.getValue());
			}

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // a string
		return true;
	}

	public void indexPage(String url, String[] unique_words) {
		indexACK = new Vector<String>();
		try {
			for (int i = 0; i < unique_words.length; i++) {
				JSONObject toSend = new JSONObject();
				LinkedList<String> l1 = new LinkedList<String>();
				toSend.put("type", "INDEX"); // string
				toSend.put("target_id", hashCode(unique_words[i]));
				toSend.put("sender_id", Long.toString(nodeID));
				toSend.put("keyword", unique_words[i]);
				toSend.put("link", l1);
				l1.add(url);
				send(toSend, hashCode(unique_words[i]));

			}
			ExecutorService service = Executors.newSingleThreadExecutor();
			final String[] uWords = unique_words;
			try {
				Runnable r = new Runnable() {
					@Override
					public void run() {
						while (indexACK.size() < uWords.length)
							;
					}
				};

				Future<?> f = service.submit(r);

				f.get(30, TimeUnit.SECONDS); // attempt the task for two minutes
			} catch (final InterruptedException e) {
				// The thread was interrupted during sleep, wait or join
			} catch (final TimeoutException e) {
				// Took too long!
				for (int i = 0; i < unique_words.length; i++) {
					if (!indexACK.contains(unique_words[i])) {
						try {
							JSONObject toSend = new JSONObject();
							toSend.put("type", "PING");
							toSend.put("target_id", hashCode(unique_words[i]));
							toSend.put("sender_id", Long.toString(nodeID));

							toSend.put("ip_address", InetAddress.getLocalHost()
									.getHostAddress().toString());

							send(toSend, hashCode(unique_words[i]));
							Thread.sleep(10000);
							// If no ACK - assume target node is dead - remove
							// from
							// routing table.
							synchronized (pingACK) {

								if (!pingACK.get((String) toSend
										.get("ip_address"))) {
									routingTable
											.remove(findNearest(hashCode(unique_words[i])));
								}

							}
							// rerun following ping
							indexPage(url, unique_words);
						} catch (UnknownHostException | InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}
			} catch (final ExecutionException e) {
				// An exception from within the Runnable task
			} finally {
				service.shutdown();
			}

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		}
	}

	public SearchResult[] search(String[] words) {
		final String[] tmpWords = words;
		searchResult = new Vector<SearchResult>();
		try {
			for (int i = 0; i < words.length; i++) {

				JSONObject toSend = new JSONObject();
				toSend.put("type", "SEARCH");
				toSend.put("word", words[i]); // The word to search for
				toSend.put("node_id", hashCode(words[i])); // target node id
				toSend.put("sender_id", nodeID); // a non-negative number of
													// order
													// 2'^32^', of this message
													// originator
				send(toSend, hashCode(words[i]));
			}

			ExecutorService service = Executors.newSingleThreadExecutor();

			try {
				Runnable r = new Runnable() {
					@Override
					public void run() {
						while (searchResult.size() < tmpWords.length)
							;
					}
				};

				Future<?> f = service.submit(r);

				f.get(30, TimeUnit.SECONDS); // attempt the task for two minutes
			} catch (final InterruptedException e) {
				// The thread was interrupted during sleep, wait or join
			} catch (final TimeoutException e) {
				// Took too long!
				for (int i = 0; i < words.length; i++) {
					if (!searchResult.contains(words[i])) {
						try {
							JSONObject toSend = new JSONObject();
							toSend.put("type", "PING");
							toSend.put("target_id", hashCode(words[i]));
							toSend.put("sender_id", Long.toString(nodeID));

							toSend.put("ip_address", InetAddress.getLocalHost()
									.getHostAddress().toString());

							send(toSend, hashCode(words[i]));
							Thread.sleep(10000);
							// If no ACK - assume target node is dead - remove
							// from
							// routing table.
							synchronized (pingACK) {

								if (!pingACK.get((String) toSend
										.get("ip_address"))) {
									routingTable
											.remove(findNearest(hashCode(words[i])));
								}

							}
							// rerun following ping
							return search(words);
						} catch (UnknownHostException | InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}
			} catch (final ExecutionException e) {
				// An exception from within the Runnable task
			} finally {
				service.shutdown();
			}

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return searchResult.toArray(new SearchResult[searchResult.size()]);
	}

	public int hashCode(String str) {
		int hash = 0;
		for (int i = 0; i < str.length(); i++) {
			hash = hash * 31 + str.charAt(i);
		}
		return Math.abs(hash);
	}

	private Long findNearest(long key) {
		Entry<Long, InetSocketAddress> low = routingTable.floorEntry(key);
		Entry<Long, InetSocketAddress> high = routingTable.ceilingEntry(key);
		Long res = (long) 0;
		if (low != null && high != null) {
			res = Math.abs(key - low.getKey()) < Math.abs(key - high.getKey()) ? low
					.getKey() : high.getKey();
		} else if (low != null || high != null) {
			res = low != null ? low.getKey() : high.getKey();
		}
		return res;
	}

	// used to send messages via nodeID
	private void send(JSONObject toSend, long targetNode) {
		try {
			DatagramSocket socket = new DatagramSocket();
			byte[] contents = toSend.toString().getBytes();
			DatagramPacket message;

			message = new DatagramPacket(contents, contents.length,
					routingTable.get(findNearest(targetNode)));
			socket.send(message);
			socket.close();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// used to send messages to nodes not in routing table
	private void send(JSONObject toSend, InetSocketAddress targetNode) {
		try {
			DatagramSocket socket = new DatagramSocket();
			byte[] contents = toSend.toString().getBytes();
			DatagramPacket message;

			message = new DatagramPacket(contents, contents.length, targetNode);
			socket.send(message);
			socket.close();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private class Listening extends Thread {
		DatagramSocket udp_socket;
		boolean isRunning = true;

		private Listening(DatagramSocket udp_socket) {
			this.udp_socket = udp_socket;
		}

		public void run() {
			System.out.println("started");
			DatagramPacket wPacket = null;
			byte[] wBuffer = null;

			wBuffer = new byte[2048];
			wPacket = new DatagramPacket(wBuffer, wBuffer.length);

			while (isRunning) {
				try {

					udp_socket.receive(wPacket);
					Thread t = new Handler(wPacket);
					t.start();
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}

		}
	}

	private class Handler extends Thread {

		JSONObject data;

		private Handler(DatagramPacket packet) {

			try {
				String response = new String(packet.getData(), 0,
						packet.getLength(), "UTF-8");
				data = new JSONObject(response);
			} catch (UnsupportedEncodingException | JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		public void run() {
			System.out.println("messaged recieved: " + data.toString());

			try {

				// The intention had been to identify if this was the target
				// node at this point, however, due to not all messages types
				// using the same field identifier for this information, this is
				// not possible
				JSONObject toSend;

				switch ((String) data.get("type")) {
				case "JOINING_NETWORK":

					toSend = new JSONObject();
					toSend.put("type", "JOINING_NETWORK_RELAY");
					toSend.put("node_id", data.getString("node_id"));
					toSend.put("gateway_id", Long.toString(nodeID));

					send(toSend, Long.getLong(data.getString("node_id")));
					routingTable.put(Long.getLong(data.getString("node_id")),
							new InetSocketAddress(data.getString("ip_address"),
									8787));
					break;
				case "JOINING_NETWORK_RELAY":
					if (findNearest(Long.getLong(data.getString("node_id"))) != nodeID) {
						toSend = new JSONObject();
						toSend.put("type", "JOINING_NETWORK_RELAY");
						toSend.put("node_id", data.getString("node_id"));
						toSend.put("gateway_id", data.getString("gateway_id"));

						send(toSend, Long.getLong(data.getString("node_id")));
					}
					toSend = new JSONObject();

					LinkedList<LinkedHashMap<String, String>> l1 = new LinkedList<LinkedHashMap<String, String>>();
					toSend.put("type", "ROUTING_INFO");
					toSend.put("node_id", data.getString("node_id"));
					toSend.put("gateway_id", data.getString("gateway_id"));
					toSend.put("ip_address", InetAddress.getLocalHost()
							.toString());

					// m1.put(key, value)
					for (Entry<Long, InetSocketAddress> entry : routingTable
							.entrySet()) {
						LinkedHashMap<String, String> m1 = new LinkedHashMap<String, String>();
						m1.put("node_id", Long.toString(entry.getKey()));
						m1.put("ip_address", entry.getValue().getAddress()
								.toString());
						l1.add(m1);
					}
					toSend.put("route_table", l1);

					send(toSend, Long.getLong(data.getString("gateway_id")));
					break;
				case "ROUTING_INFO":
					if (Long.getLong(data.getString("gateway_id")) == nodeID) {
						send(data, Long.getLong(data.getString("node_id")));
					} else if (Long.getLong(data.getString("node_id")) == nodeID) {

					} else {
						send(data, Long.getLong(data.getString("gateway_id")));
					}

					// All nodes add any routing info that passes through them
					// to their routing tables.
					JSONArray table = data.getJSONArray("route_table");
					for (int i = 0; i < table.length(); i++) {
						JSONObject entry = table.getJSONObject(i);
						routingTable.put(Long.valueOf(entry
								.getString("node_id")), new InetSocketAddress(
								entry.getString("ip_address"), 8787));
					}
					break;
				case "LEAVING_NETWORK":
					routingTable
							.remove(Long.valueOf(data.getString("node_id")));
					break;
				case "INDEX":
					if (findNearest(Long.getLong(data.getString("target_id"))) == nodeID) {
						JSONArray index = data.getJSONArray("link");
						if (!indexTable.containsKey(data.getString("keyword"))) {
							indexTable.put(data.getString("keyword"),
									new HashMap<String, Integer>());
						}
						for (int i = 0; i < index.length(); i++) {
							if (indexTable.get(data.getString("keyword"))
									.containsKey(index.getString(i))) {
								int tmp = indexTable.get(
										data.getString("keyword")).get(
										index.getString(i));
								indexTable.get(data.getString("keyword")).put(
										index.getString(i), tmp++);
							}
							indexTable.get(data.getString("keyword")).put(
									index.getString(i), 1);
						}

						toSend = new JSONObject();
						toSend.put("type", "ACK_INDEX");
						toSend.put("node_id", (String) data.get("target_id"));
						toSend.put("keyword", data.getString("keyword"));
						send(toSend, Long.valueOf(data.getString("sender_id")));

					} else {
						send(data, Long.getLong(data.getString("target_id")));
					}
					break;
				case "SEARCH":
					if (findNearest(Long.getLong(data.getString("node_id"))) == nodeID) {
						toSend = new JSONObject();
						LinkedList<LinkedHashMap<String, String>> l2 = new LinkedList<LinkedHashMap<String, String>>();
						toSend.put("type", "SEARCH_RESPONSE");
						toSend.put("word", data.getString("word"));
						toSend.put("node_id", data.getString("sender_id"));
						toSend.put("sender_id", Long.toString(nodeID));
						for (Entry<String, Integer> entry : indexTable.get(
								data.getString("word")).entrySet()) {
							LinkedHashMap<String, String> m1 = new LinkedHashMap<String, String>();
							m1.put("url", entry.getKey());
							m1.put("rank", Integer.toString(entry.getValue()));
							l2.add(m1);
						}
						toSend.put("route_table", l2);
					} else {
						send(data, Long.getLong(data.getString("node_id")));
					}
					break;
				case "SEARCH_RESPONSE":
					if (findNearest(Long.getLong(data.getString("node_id"))) == nodeID) {
						toSend = new JSONObject();
						toSend.put("type", "ACK_INDEX");
						toSend.put("node_id", (String) data.get("target_id"));
						toSend.put("keyword", data.getString("keyword"));
						send(toSend, Long.valueOf(data.getString("sender_id")));

						JSONArray result = data.getJSONArray("response");
						String[] resultArray = new String[result.length()];
						for (int i = 0; i < result.length(); i++) {
							resultArray[i] = result.getString(i);

						}
						searchResult.add(new SearchResult(data
								.getString("word"), resultArray, 0));

					} else {
						send(data, Long.getLong(data.getString("node_id")));
					}
					break;
				case "PING":

					InetSocketAddress toAddress;

					toSend = new JSONObject();
					toSend.put("type", "ACK");
					toSend.put("node_id", (String) data.get("target_id"));
					toSend.put("ip_address", InetAddress.getLocalHost()
							.getHostAddress().toString());
					toAddress = new InetSocketAddress(
							InetAddress.getByName((String) data
									.get("ip_address")), 8787);

					send(toSend, toAddress);
					System.out.println("Sending: " + toSend.toString());

					long to = findNearest(Long.parseLong((String) data
							.get("target_id")));

					if (nodeID != to) {
						toSend = new JSONObject();
						toSend.put("type", "PING");
						toSend.put("target_id", (String) data.get("target_id"));
						toSend.put("sender_id", (String) data.get("sender_id"));
						toSend.put("ip_address", InetAddress.getLocalHost()
								.getHostAddress().toString());

						toAddress = new InetSocketAddress(
								InetAddress.getByName((String) data
										.get("ip_address")), 8787);

						send(toSend, toAddress);
						System.out.println("Sending: " + toSend.toString());

						// Wait for ACK
						synchronized (pingACK) {
							pingACK.put((String) toSend.get("ip_address"),
									false);
						}
						System.out.println("ping" + pingACK.toString());
						Thread.sleep(10000);
						// If no ACK - assume target node is dead - remove from
						// routing table.
						synchronized (pingACK) {

							if (!pingACK.get((String) toSend.get("ip_address"))) {
								routingTable.remove(to);
								System.out.println("Node removed");
							} else {
								System.out.println("Ping ACKed");
							}
						}
					}
					break;
				case "ACK_INDEX":
					break;
				case "ACK":

					synchronized (pingACK) {
						if (pingACK
								.containsKey((String) data.get("ip_address")))
							pingACK.put((String) data.get("ip_address"), true);
						System.out.println("ACK Recieved: " + data.toString());
					}
					break;
				default:
					break;
				}

			} catch (JSONException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
