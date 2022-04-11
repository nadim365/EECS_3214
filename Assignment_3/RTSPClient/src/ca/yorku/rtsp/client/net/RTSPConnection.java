/*
 * Author: Jonatan Schroeder
 * Updated: March 2022
 *
 * This code may not be used without written consent of the authors.
 */

package ca.yorku.rtsp.client.net;

import ca.yorku.rtsp.client.exception.RTSPException;
import ca.yorku.rtsp.client.model.Frame;
import ca.yorku.rtsp.client.model.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.ThreadLocalRandom;

import javax.xml.crypto.Data;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

    private static final int BUFFER_LENGTH = 0x10000;

    private Session session;

    // TODO Add additional fields, if necessary
    private String server;
    private String videoName;
    private int sessionNumber;
    private int CSeq = 0;
    private Socket rtspTCP_socket; // socket for RTSP using TCP
    private DatagramSocket rtpUDP_datagram_socket; // datagram socket for RTP using UDP
    private PrintWriter outputStreamTCP;
    private BufferedReader inputStreamTCP;

    RTPReceivingThread myThread = new RTPReceivingThread();

    /**
     * Establishes a new connection with an RTSP server. No message is sent at this
     * point, and no stream is set up.
     *
     * @param session The Session object to be used for connectivity with the UI.
     * @param server  The hostname or IP address of the server.
     * @param port    The TCP port number where the server is listening to.
     * @throws RTSPException If the connection couldn't be accepted, such as if the
     *                       host name or port number are invalid
     *                       or there is no connectivity.
     */
    public RTSPConnection(Session session, String server, int port) throws RTSPException {
        this.session = session;
        this.server = server;
        try {
            rtspTCP_socket = new Socket(server, port);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RTSPException("Could not establish connection with server." + e);
        }
    }

    /**
     * Sends a SETUP request to the server. This method is responsible for sending
     * the SETUP request, receiving the
     * response and retrieving the session identification to be used in future
     * messages. It is also responsible for
     * establishing an RTP datagram socket to be used for data transmission by the
     * server. The datagram socket should be
     * created with a random UDP port number, and the port number used in that
     * connection has to be sent to the RTSP
     * server for setup. This datagram socket should also be defined to timeout
     * after 1 second if no packet is
     * received.
     *
     * @param videoName The name of the video to be setup.
     * @throws RTSPException If there was an error sending or receiving the RTSP
     *                       data, or if the RTP socket could not be
     *                       created, or if the server did not return a successful
     *                       response.
     */
    public synchronized void setup(String videoName) throws RTSPException {
        this.videoName = videoName;
        int randPortNum = ThreadLocalRandom.current().nextInt(1025, 35536);
        CSeq++;
        String request;
        String serverResponse;
        int responseCode;

        // Establish RTP UDP datagram socket
        try {
            rtpUDP_datagram_socket = new DatagramSocket(randPortNum);
            // rtpUDP_datagram_socket = new DatagramSocket();
            rtpUDP_datagram_socket.setSoTimeout(1000);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RTSPException("Failed to create RTP datagram socket" + e);
        }

        try {
            // Send SETUP request on RTSP TCP socket
            outputStreamTCP = new PrintWriter(rtspTCP_socket.getOutputStream(), true);
            inputStreamTCP = new BufferedReader(new InputStreamReader(rtspTCP_socket.getInputStream()));

            request = "SETUP " + videoName + " RTSP/1.0\nCSeq: " + CSeq + "\nTransport: RTP/UDP; client_port= "
                    + randPortNum + "\n";
            System.out.println(request); // Debugging and terminal logging
            outputStreamTCP.println(request);

            // Response, add into list line by line
            ArrayList<String> responseList = new ArrayList<String>();

            // TODO: CHECK WHILE LOOP

            serverResponse = inputStreamTCP.readLine();
            while (!(serverResponse.equals(""))) {
                System.out.println(serverResponse);
                responseList.add(serverResponse);
                serverResponse = inputStreamTCP.readLine();
            }

            // do {
            // serverResponse = inputStreamTCP.readLine();
            // System.out.println(serverResponse);
            // responseList.add(serverResponse);
            // } while (!(serverResponse.equals("")));

            // Check response code
            String[] responseArray = responseList.get(0).split("\\s+");
            responseCode = Integer.parseInt(responseArray[1]);
            if (responseCode != 200) {
                throw new RTSPException(responseList.get(0));
            }

            // Get session number
            responseArray = responseList.get(2).split("\\s+");
            sessionNumber = Integer.parseInt(responseArray[1]);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RTSPException(e);
        }

    }

    /**
     * Sends a PLAY request to the server. This method is responsible for sending
     * the request, receiving the response
     * and, in case of a successful response, starting a separate thread responsible
     * for receiving RTP packets with
     * frames.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP
     *                       data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void play() throws RTSPException {
        // TODO
        CSeq++;
        String request;
        String serverResponse;
        int responseCode;

        try {

            request = "PLAY " + videoName + " RTSP/1.0\nCSeq: " + CSeq + "\nSession: " + sessionNumber + "\n";
            System.out.println(request);

            // Send PLAY request on RTSP TCP socket
            outputStreamTCP.println(request);

            // Response, add into list line by line

            ArrayList<String> responseList = new ArrayList<String>();
            // TODO: CHECK WHILE LOOP

            serverResponse = inputStreamTCP.readLine();
            while (!(serverResponse.equals(""))) {
                System.out.println(serverResponse);
                responseList.add(serverResponse);
                serverResponse = inputStreamTCP.readLine();
            }

            // do {
            // serverResponse = inputStreamTCP.readLine();
            // System.out.println(serverResponse);
            // responseList.add(serverResponse);
            // } while (!(serverResponse.equals("")));

            // Check response code
            String[] responseArray = responseList.get(0).split("\\s+");
            responseCode = Integer.parseInt(responseArray[1]);
            if (responseCode != 200) {
                throw new RTSPException(responseList.get(0));
            }

            // // Get session number
            // responseArray = responseList.get(3).split("\\s+");
            // sessionNumber = Integer.parseInt(responseArray[1]);

            // TODO: Fish implementing run() in RTPReceivingThread()
            myThread = new RTPReceivingThread();
            myThread.start();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RTSPException(e);
        }
    }

    private class RTPReceivingThread extends Thread {
        /**
         * Continuously receives RTP packets until the thread is cancelled. Each packet
         * received from the datagram
         * socket is assumed to be no larger than BUFFER_LENGTH bytes. This data is then
         * parsed into a Frame object
         * (using the parseRTPPacket method) and the method session.processReceivedFrame
         * is called with the resulting
         * packet. The receiving process should be configured to timeout if no RTP
         * packet is received after two seconds.
         */
        @Override
        public void run() {
            // TODO
            byte[] responseBuffer = new byte[BUFFER_LENGTH];
            DatagramPacket rtpDatagramPacketResponse = new DatagramPacket(responseBuffer, responseBuffer.length);

            while (true) {
                try {
                    rtpUDP_datagram_socket.setSoTimeout(2000);
                    rtpUDP_datagram_socket.receive(rtpDatagramPacketResponse);

                    byte[] data = new byte[rtpDatagramPacketResponse.getLength()];
                    System.arraycopy(rtpDatagramPacketResponse.getData(), rtpDatagramPacketResponse.getOffset(), data,
                            0, rtpDatagramPacketResponse.getLength());

                    DatagramPacket packet2 = new DatagramPacket(data, data.length);
                    session.processReceivedFrame(parseRTPPacket(packet2));
                    // session.processReceivedFrame(parseRTPPacket(rtpDatagramPacketResponse));
                    sleep(40);
                } catch (Exception e) {
                    break;
                }
            }
        }

    }

    /**
     * Sends a PAUSE request to the server. This method is responsible for sending
     * the request, receiving the response
     * and, in case of a successful response, stopping the thread responsible for
     * receiving RTP packets with frames.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP
     *                       data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void pause() throws RTSPException {
        CSeq++;
        String request;
        String serverResponse;
        int responseCode;
        request = "PAUSE " + videoName + " RTSP/1.0\nCSeq: " + CSeq + "\nSession: " + sessionNumber + "\n";

        try {
            System.out.println(request);

            // Send PAUSE request on RTSP TCP socket
            outputStreamTCP.println(request);

            // Response, add into list line by line
            ArrayList<String> responseList = new ArrayList<String>();
            do {
                serverResponse = inputStreamTCP.readLine();
                System.out.println(serverResponse);
                responseList.add(serverResponse);
            } while (!(serverResponse.equals("")));

            // Check response code
            String[] responseArray = responseList.get(0).split("\\s+");
            responseCode = Integer.parseInt(responseArray[1]);
            if (responseCode != 200) {
                throw new RTSPException(responseList.get(0));
            }

            // // Get session number
            // responseArray = responseList.get(3).split("\\s+");
            // sessionNumber = Integer.parseInt(responseArray[1]);

        } catch (Exception e) {
            throw new RTSPException(e);
        }
        myThread.interrupt();
    }

    /**
     * Sends a TEARDOWN request to the server. This method is responsible for
     * sending the request, receiving the
     * response and, in case of a successful response, closing the RTP socket. This
     * method does not close the RTSP
     * connection, and a further SETUP in the same connection should be accepted.
     * Also this method can be called both
     * for a paused and for a playing stream, so the thread responsible for
     * receiving RTP packets will also be
     * cancelled.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP
     *                       data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void teardown() throws RTSPException {

        // TODO
        CSeq++;
        String request;
        String serverResponse;
        int responseCode;
        request = "TEARDOWN " + videoName + " RTSP/1.0\nCSeq: " + CSeq + "\nSession: " + sessionNumber + "\n";

        try {
            System.out.println(request);

            // Send TEARDOWN request on RTSP TCP socket
            outputStreamTCP.println(request);

            // Response, add into list line by line
            ArrayList<String> resposneList = new ArrayList<>();
            serverResponse = inputStreamTCP.readLine();
            while (!(serverResponse.equals(""))) {
                System.out.println(serverResponse);
                resposneList.add(serverResponse);
                serverResponse = inputStreamTCP.readLine();
            }

            responseCode = Integer.parseInt(resposneList.get(0).split("\\s+")[1]);

            if (responseCode != 200) {
                throw new RTSPException(resposneList.get(0));
            }

            rtpUDP_datagram_socket.close();
            myThread.interrupt();

        } catch (Exception e) {
            // TODO: handle exception
            throw new RTSPException("Error in teardown");
        }
    }

    /**
     * Closes the connection with the RTSP server. This method should also close any
     * open resource associated to this
     * connection, such as the RTP connection, if it is still open.
     */
    public synchronized void closeConnection() {
        // TODO
    }

    /**
     * Parses an RTP packet into a Frame object.
     *
     * @param packet the byte representation of a frame, corresponding to the RTP
     *               packet.
     * @return A Frame object.
     */
    public static Frame parseRTPPacket(DatagramPacket packet) {

        // TODO
        Frame result;
        // byte[] respByteArray = new byte[BUFFER_LENGTH];
        byte[] respByteArray = new byte[packet.getLength()];
        ByteBuffer respBuffer = ByteBuffer.allocate(packet.getLength());
        BitSet bitset;

        boolean marker;
        short sequenceNumber;
        int timestamp;
        byte payloadType = 26;
        // int offset;
        // int length;

        respByteArray = packet.getData(); // put data into response byte array
        respBuffer = ByteBuffer.wrap(respByteArray); // back response buffer with byte arr

        respBuffer.order(ByteOrder.BIG_ENDIAN);

        // respBuffer.flip(); // set buffer to read mode

        // byte[] rtpPacketHeader = new byte[12];
        // respBuffer.get(0, rtpPacketHeader, 0, 12); //
        // bitset = BitSet.valueOf(rtpPacketHeader);

        bitset = BitSet.valueOf(respByteArray);

        // Get Marker
        marker = bitset.get(8);
        System.out.println(marker);

        // Get sequence number
        sequenceNumber = ByteBuffer.wrap(respByteArray, 2, 2).getShort();
        // String seqNumString = "";
        // for (int i = 16; i < 32; i++) {
        // boolean value = bitset.get(i);
        // if (value) {
        // seqNumString += "1";
        // } else {
        // seqNumString += "0";
        // }
        // }
        // sequenceNumber = Short.parseShort(seqNumString, 2);
        System.out.println(sequenceNumber);

        // Get timestamp
        timestamp = ByteBuffer.wrap(respByteArray, 4, 4).getInt();
        System.out.println(timestamp);

        // String timestampString = "";
        // for (int i = 33; i < 35; i++) {
        // boolean value = bitset.get(i);
        // if (value) {
        // timestampString += "1";
        // } else {
        // timestampString += "0";
        // }
        // }
        // timestamp = Integer.parseInt(timestampString);

        // Byte payloadType = respBuffer.ge

        // Payload
        // byte[] payloadByteArr = new byte[respBuffer.limit() - 12];
        // // respBuffer.get(13, payloadByteArr);
        // respBuffer.get(12, payloadByteArr, 0, (respBuffer.limit() - 12));

        // result = new Frame(payloadType, marker, sequenceNumber, timestamp,
        // payloadByteArr);
        result = new Frame(payloadType, marker, sequenceNumber, timestamp, respByteArray, 12, respBuffer.limit() - 12);
        // result = new Frame

        return result; // Replace with a proper Frame
    }

    /**
     * Reads and parses an RTSP response from the socket's input.
     *
     * @return An RTSPResponse object if the response was read completely, or null
     *         if the end of the stream was reached.
     * @throws IOException   In case of an I/O error, such as loss of connectivity.
     * @throws RTSPException If the response doesn't match the expected format.
     */
    public RTSPResponse readRTSPResponse() throws IOException, RTSPException {

        // TODO
        return null; // Replace with a proper RTSPResponse
    }

}
