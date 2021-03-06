package ca.yorku.eecs.dnslookup;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public class DNSLookupProcess implements Closeable {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL_NS = 10;
    private static final int MAX_QUERY_ATTEMPTS = 3;
    protected static final int SO_TIMEOUT = 5000;
    private static int queryId = 0;

    private final DNSCache cache = DNSCache.getInstance();
    private final Random random = new SecureRandom();
    private final DNSProcessListener listener;
    private final DatagramSocket socket;
    private InetAddress nameServer;

    /**
     * Creates a new lookup service. Also initializes the datagram socket object
     * with a default timeout.
     *
     * @param nameServer The nameserver to be used initially. If set to null, "root"
     *                   or "random", will choose a random
     *                   pre-determined root nameserver.
     * @param listener   A DNSProcessListener listener object with methods to be
     *                   called at key events in the query
     *                   processing.
     * @throws SocketException      If a DatagramSocket cannot be created.
     * @throws UnknownHostException If the nameserver is not a valid server.
     */
    public DNSLookupProcess(String nameServer, DNSProcessListener listener)
            throws SocketException, UnknownHostException {
        this.listener = listener;
        socket = new DatagramSocket();
        socket.setSoTimeout(SO_TIMEOUT);
        this.setNameServer(nameServer);
    }

    /**
     * Returns the nameserver currently being used for queries.
     *
     * @return The string representation of the nameserver IP address.
     */
    public String getNameServer() {
        return this.nameServer.getHostAddress();
    }

    /**
     * Updates the nameserver to be used in all future queries.
     *
     * @param nameServer The nameserver to be used initially. If set to null, "root"
     *                   or "random", will choose a random
     *                   pre-determined root nameserver.
     * @throws UnknownHostException If the nameserver is not a valid server.
     */
    public void setNameServer(String nameServer) throws UnknownHostException {

        // If none provided, choose a random root nameserver
        if (nameServer == null || nameServer.equalsIgnoreCase("random") || nameServer.equalsIgnoreCase("root")) {
            List<ResourceRecord> rootNameServers = cache.getCachedResults(DNSCache.rootQuestion, false);
            nameServer = rootNameServers.get(0).getTextResult();
        }
        this.nameServer = InetAddress.getByName(nameServer);
    }

    /**
     * Closes the lookup service and related sockets and resources.
     */
    public void close() {
        socket.close();
    }

    /**
     * Finds all the result for a specific node. If there are valid (not expired)
     * results in the cache, uses these
     * results, otherwise queries the nameserver for new records. If there are CNAME
     * records associated to the question,
     * they are included in the results as CNAME records (i.e., not queried
     * further).
     *
     * @param question Host and record type to be used for search.
     * @return A (possibly empty) set of resource records corresponding to the
     *         specific query requested.
     */
    public Collection<ResourceRecord> getDirectResults(DNSQuestion question) {

        Collection<ResourceRecord> results = cache.getCachedResults(question, true);
        if (results.isEmpty()) {
            iterativeQuery(question, nameServer);
            results = cache.getCachedResults(question, true);
        }
        return results;
    }

    /**
     * Finds all the result for a specific node. If there are valid (not expired)
     * results in the cache, uses these
     * results, otherwise queries the nameserver for new records. If there are CNAME
     * records associated to the question,
     * they are retrieved recursively for new records of the same type, and the
     * returning set will contain both the
     * CNAME record and the resulting addresses.
     *
     * @param question             Host and record type to be used for search.
     * @param maxIndirectionLevels Number of CNAME indirection levels to support.
     * @return A set of resource records corresponding to the specific query
     *         requested.
     * @throws CnameIndirectionLimitException If the number CNAME redirection levels
     *                                        exceeds the value set in
     *                                        maxIndirectionLevels.
     */
    public Collection<ResourceRecord> getRecursiveResults(DNSQuestion question, int maxIndirectionLevels)
            throws CnameIndirectionLimitException {

        if (maxIndirectionLevels < 0)
            throw new CnameIndirectionLimitException();

        Collection<ResourceRecord> directResults = getDirectResults(question);
        if (directResults.isEmpty() || question.getRecordType() == RecordType.CNAME)
            return directResults;

        List<ResourceRecord> newResults = new ArrayList<>();
        for (ResourceRecord record : directResults) {
            newResults.add(record);
            if (record.getRecordType() == RecordType.CNAME) {
                newResults.addAll(getRecursiveResults(
                        new DNSQuestion(record.getTextResult(), question.getRecordType(), question.getRecordClass()),
                        maxIndirectionLevels - 1));
            }
        }
        return newResults;
    }

    /**
     * Retrieves DNS results from a specified DNS server using the iterative mode.
     * After an individual query is sent and
     * its response is received (or times out), checks if an answer for the
     * specified host exists. Resulting values
     * (including answers, nameservers and additional information provided by the
     * nameserver) are added to the cache.
     * <p>
     * If after the first query an answer exists to the original question (either
     * with the same record type or an
     * equivalent CNAME record), the function returns with no further actions. If
     * there is no answer after the first
     * query but the response returns at least one nameserver, a follow-up query for
     * the same question must be done to
     * another nameserver.
     * <p>
     * Note that nameservers returned by the response contain text records linking
     * to the host names of these servers.
     * If at least one nameserver provided by the response to the first query has a
     * known IP address (either from this
     * query or from a previous query), it must be used first, otherwise additional
     * queries are required to obtain the
     * IP address of the nameserver before it is queried. Only one nameserver must
     * be contacted for the follow-up
     * query.
     *
     * @param question      Host name and record type/class to be used for the
     *                      query.
     * @param serverAddress Address of the server to be used for the first query.
     */
    protected void iterativeQuery(DNSQuestion question, InetAddress serverAddress) {

        /* TO BE COMPLETED BY THE STUDENT */

    }

    /**
     * Handles the process of sending an individual DNS query to a single question.
     * Builds and sends the query (request)
     * message, then receives and parses the response. Received responses that do
     * not match the requested transaction ID
     * are ignored. If no response is received after SO_TIMEOUT milliseconds, the
     * request is sent again, with the same
     * transaction ID. The query should be sent at most MAX_QUERY_ATTEMPTS times,
     * after which the function should return
     * without changing any values. If a response is received, all of its records
     * are added to the cache.
     * <p>
     * The method listener.beforeSendingQuery() must be called every time a new
     * query message is about to be sent.
     *
     * @param question      Host name and record type/class to be used for the
     *                      query.
     * @param serverAddress Address of the server to be used for the query.
     * @return If no response is received, returns null. Otherwise, returns a set of
     *         resource records for all
     *         nameservers received in the response. Only records found in the
     *         nameserver section of the response are included,
     *         and only those whose record type is NS. If a response is received but
     *         there are no nameservers, returns an empty
     *         set.
     * @throws IOException
     * @throws InterruptedException
     */
    protected Set<ResourceRecord> individualQueryProcess(DNSQuestion question, InetAddress serverAddress)
            throws IOException {
        /* TO BE COMPLETED BY THE STUDENT */

        // building and sending the query message
        // DatagramSocket socket = new DatagramSocket();
        ByteBuffer qBuffer = ByteBuffer.allocate(512);
        int id = buildQuery(qBuffer, question);
        byte[] qByteArr = qBuffer.array(); // put query qBuffer into byte array qByteArr
        DatagramPacket packet = new DatagramPacket(qByteArr, qByteArr.length, serverAddress, DEFAULT_DNS_PORT); // intialize
                                                                                                                // packet
                                                                                                                // for
                                                                                                                // sending
                                                                                                                // query

        // Response Buffer Declaration & Init
        byte[] rByteArray = new byte[512]; // initalize response byte array
        ByteBuffer responseBuffer = ByteBuffer.allocate(512); // decl response buffer
        DatagramPacket responsePacket = new DatagramPacket(rByteArray, rByteArray.length); // create new packet for
                                                                                           // response

        // Send and try to receive packet. Ignore if ID not equal. If timeout, resend
        // packet.
        // TODO: Does the timeout work as intended??

        int no_attempts = 0;
        while (no_attempts >= MAX_QUERY_ATTEMPTS) {
            listener.beforeSendingQuery(question, serverAddress, id);
            socket.send(packet);
            socket.setSoTimeout(SO_TIMEOUT);
            try {
                // listener.wait(SO_TIMEOUT);
                socket.receive(responsePacket); // receive response
                // TODO: line 245 maybe redundant, combine into one .wrap(packet.getData()),
                // rByteArray not needed?
                rByteArray = responsePacket.getData(); // put data into response byte array
                responseBuffer = ByteBuffer.wrap(rByteArray); // put byte arr into response buffer
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                continue;
            }
            // } catch (InterruptedException e) {
            // e.printStackTrace();
            // continue;
            // }
            // packet.setData(rBytes);
            // socket.receive(packet);

            // checking if ID of response is valid.
            int responseID = ByteBuffer.wrap(rByteArray, 0, 2).getShort(); // getting ID of response
            if (id != responseID) {
                no_attempts++;
                continue;
            } else {
                // TODO: Check byte/numbers/positions
                int flags = ByteBuffer.wrap(rByteArray, 2, 2).getShort(); // getting QR Opcode AA TC RD RA

                return processResponse(responseBuffer);
            }
        }

        // Switch Buffers to read mode
        // qBuffer.flip();
        // responseBuffer.flip();

        // recevieng and parsing the response message
        // rBytes = packet.getData();

        // int num_answers = ByteBuffer.wrap(rBytes, 6, 2).getShort(); // getting number
        // of answers ANCOUNT (6-8)
        // int num_authority = ByteBuffer.wrap(rBytes, 8, 2).getShort(); // getting
        // number of authority NSCOUNT (8-10)
        // int num_additional = ByteBuffer.wrap(rBytes, 10, 2).getShort(); // getting
        // number of additional ARCOUNT (10-12)
        // int qoffset = 12; // offset to start query. Header is 12 Bytes

        // //TODO: verify dis shit, convert string to ASCII bytes?? then count number of
        // bytes?
        // //int qType = qoffset + question.getHostName().length();
        // byte[] questionHostName =
        // question.getHostName().getBytes(StandardCharsets.US_ASCII);
        // int qType = qoffset + questionHostName.length; // 12 + the question length to
        // get to qtype position
        // int qClass = qType + 2; //
        // int aoffset = qClass + 2; // offset to start answers.
        // int total_records = num_answers + num_authority + num_additional;

        // while (total_records != 0) {

        // }

        // // Put resp q in bytes into buffer
        // ByteBuffer responseQuestionBuffer = ByteBuffer.allocate(qNameLength);
        // ByteBuffer.wrap(responseQuestionInBytes);
        // responseQuestionBuffer.flip();

        // int qNameLength = (qBuffer.limit() + 1) - 12 - 2 - 2;
        // responseBuffer.position(qBuffer.limit() + qNameLength);
        // int timeToLive = responseBuffer.getInt();

        // TODO: LOL FIX THIS DONT USE THIS. JUST USE IT TO PRY INTO RESOURCE RECORDS
        // ResourceRecord resourceRecResult = new ResourceRecord(question, timeToLive,
        // "urmumgey");
        // result.add(resourceRecResult);

        // close();

        // TODO: return result?? Return the set<resource record> ??
        return null;
    }

    /**
     * Fills a ByteBuffer object with the contents of a DNS query. The buffer must
     * be updated from the start (position
     * 0). A random transaction ID must also be generated and filled in the
     * corresponding part of the query. The query
     * must be built as an iterative (non-recursive) request for a regular query
     * with a single question. When the
     * function returns, the buffer's position (`queryBuffer.position()`) must be
     * equivalent to the size of the query
     * data.
     *
     * @param queryBuffer The ByteBuffer object where the query will be saved.
     * @param question    Host name and record type/class to be used for the query.
     * @return The transaction ID used for the query.
     */
    protected int buildQuery(ByteBuffer queryBuffer, DNSQuestion question) {

        /* TO BE COMPLETED BY THE STUDENT */
        int ID = ThreadLocalRandom.current().nextInt(0, 65535 + 1); // generate ID for query header
        queryBuffer.putShort((short) ID); // adding ID to ByetBuffer object
        queryBuffer.putShort((short) 0); // QR OPCODE AA TC RD-not set RA Z RCODE
        queryBuffer.putShort((short) 1); // QDCOUNT
        queryBuffer.putShort((short) 0); // ANCOUNT
        queryBuffer.putShort((short) 0); // NSCOUNT
        queryBuffer.putShort((short) 0);// ARCOUNT

        // adding question to queryBuffer

        String[] hostName = question.getHostName().split("\\."); // split hostname into array of strings
        for (int i = 0; i < hostName.length; i++) {
            queryBuffer.put((byte) hostName[i].length()); // adding length of each string to buffer
            queryBuffer.put(hostName[i].getBytes()); // adding current part of hostname to buffer
        }
        queryBuffer.put((byte) 0); // adding null byte to end of hostname
        queryBuffer.putShort((short) question.getRecordType().getCode()); // adding record type to buffer
        queryBuffer.putShort((short) question.getRecordClass().getCode()); // adding record class to buffer

        if (queryBuffer.position() != queryBuffer.capacity()) {
            System.out.println("Error: queryBuffer.position() != queryBuffer.capacity()");

        }

        queryId = ID;
        return ID; // return Transaction ID of buffer

    }

    /**
     * Parses and processes a response received by a nameserver. Adds all resource
     * records found in the response message
     * to the cache. Calls methods in the listener object at appropriate points of
     * the processing sequence. Must be able
     * to properly parse records of the types: A, AAAA, NS, CNAME and MX (the
     * priority field for MX may be ignored). Any
     * other unsupported record type must create a record object with the data
     * represented as a hex string (see method
     * byteArrayToHexString).
     *
     * @param responseBuffer The ByteBuffer associated to the response received from
     *                       the server.
     * @return A set of resource records for all nameservers received in the
     *         response. Only records found in the
     *         nameserver section of the response are included, and only those whose
     *         record type is NS. If there are no
     *         nameservers, returns an empty set.
     */
    protected Set<ResourceRecord> processResponse(ByteBuffer responseBuffer) {

        /* TO BE COMPLETED BY THE STUDENT */
        Set<ResourceRecord> result = new HashSet<>();

        return null;
    }

    /**
     * Helper function that converts a hex string representation of a byte array.
     * May be used to represent the result of
     * records that are returned by the nameserver but not supported by the
     * application (e.g., SOA records).
     *
     * @param data a byte array containing the record data.
     * @return A string containing the hex value of every byte in the data.
     */
    private static String byteArrayToHexString(byte[] data) {
        return IntStream.range(0, data.length).mapToObj(i -> String.format("%02x", data[i])).reduce("", String::concat);
    }

    public static class CnameIndirectionLimitException extends Exception {
    }
}
