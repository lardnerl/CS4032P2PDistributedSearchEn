package Search;

// Lonan Hugh Lardner
// 00338834
// CS4032
// P2P Web Search System 
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import java.net.SocketException;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		try {
			DatagramSocket udp = new DatagramSocket(8787);
			PeerSearch test;
			if (args.length > 0)
				if (!args[0].equals("--boot")) {
					test = new PeerSearch(Integer.valueOf(args[1]));
					test.init(udp);
				}

			byte[] input = new byte[1024];
			System.in.read(input);
			String tmp = new String(input);
			String[] commands = tmp.split(" ");
			test = new PeerSearch(Integer.valueOf(commands[3]));
			test.joinNetwork(new InetSocketAddress(commands[1], 8787));

		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
