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
import java.io.BufferedWriter;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

    private static final int BUFFER_LENGTH = 0x10000;

    private Session session;

    // TODO Add additional fields, if necessary

    private DatagramPacket packet; // packet to be received
    private static DatagramSocket dSocket; // socket to be used to send and receive packets
    private static Socket socket; // used to establish connection with server
    private DataInputStream input; // used to read data from socket
    private DataOutputStream output; // used to write data to socket
    private BufferedReader reader; // used to read data from input
    private BufferedWriter writer; // used to write data to output
    private int cSeq; // sequence number of the next request
    private static int dSPort = 25000;
    private String video;
    private int sID; // session ID

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
        try {
            socket = new Socket(server, port);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RTSPException("Could not establish connection with server");
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

        // TODO
        cSeq = 1;
        this.video = videoName;
        String request = "SETUP " + video + " RTSP/1.0\n";
        String add1 = "CSeq: " + cSeq + "\n";
        String add2 = "Transport: RTP/UDP; client_port= " + dSPort + "\n";
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            writer.write(request);
            writer.write(add1);
            writer.write(add2);
            writer.write("\n");
            writer.flush();
            // RTSPResponse response = : first do readRTSPResponse();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RTSPException("Could not send SETUP request");
        }

        try {
            dSocket = new DatagramSocket(dSPort);
            dSocket.setSoTimeout(1000);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
            throw new RTSPException("Could not create datagram socket");
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

        // TODO
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
    }

    /**
     * Closes the connection with the RTSP server. This method should also close any
     * open resource associated to this
     * connection, such as the RTP connection, if it is still open.
     */
    public synchronized void closeConnection() {
        // TODO
        try {
            socket.close();
            dSocket.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
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
        return null; // Replace with a proper Frame
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
        String response = reader.readLine();
        if (response == null) {
            return null;
        }
        String[] resp_arr = response.split(" ", 3);
        if(resp_arr.length != 3){
            throw new RTSPException("Invalid response");
        }
        String ver = resp_arr[0];
        int status = Integer.parseInt(resp_arr[1]);
        String msg = resp_arr[2];
        RTSPResponse rtspResponse = new RTSPResponse(ver, status, msg);
        

        return null; // Replace with a proper RTSPResponse
    }

}
