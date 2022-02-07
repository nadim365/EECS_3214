package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        // TODO Add your code here
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Status response = Status.readStatus(in);
            if (response.getStatusCode() != 220) {
                throw new DictConnectionException("Expected code 220");
            } else {
                System.out.println(response.getStatusCode() + " " + response.getDetails());
            }
        } catch (Exception e) {
            // TODO: handle exception
            throw new DictConnectionException();
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {

        // TODO Add your code here
        try {            
            out.println("QUIT");                                    
            System.out.println("Client: QUIT");
            Status response = Status.readStatus(in);               
            System.out.println("Server : " + response.getStatusCode() + " " + response.getDetails());
            socket.close();
        } catch (Exception e) {
            //TODO: handle exception
            System.out.println(e);
        }                
        
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map linking database names to Database objects for all databases supported by the server, or an empty map
     * if no databases are available.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        // TODO Add your code here        
        // Check section 3.5.1 SHOW DB
        out.println("SHOW DB");
        System.out.println("Client: SHOW DB");
        Status serverResponse = Status.readStatus(in);
        String currentLine;

        System.out.println("Server: " + serverResponse.getStatusCode() + " " + serverResponse.getDetails());
        if (serverResponse.getStatusCode() == 110) {
            try {
                 currentLine = in.readLine();
                 System.out.println("Server: " + currentLine);
            } catch (Exception e){
                throw new DictConnectionException();
            }
            do {
                String [] result = DictStringParser.splitAtoms(currentLine);  
                Database dbEntry = new Database(result[0], result[1]);
                databaseMap.put(dbEntry.getName(), dbEntry);
                try {
                    currentLine = in.readLine();   
                    System.out.println("Server: " + currentLine);
                } catch (Exception e) {             
                    throw new DictConnectionException();       
                }                
            } while (!(currentLine.equals(".")));
            serverResponse = Status.readStatus(in);
            System.out.println("Server: " + serverResponse.getStatusCode() + " " + serverResponse.getDetails());
            if (serverResponse.getStatusCode() != 250) {
                throw new DictConnectionException();
            }
        } 
        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server, or an empty set if no strategies are supported.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here
        out.println("SHOW STRAT");
        System.out.println("Client: SHOW STRAT");
        Status serverResponse = Status.readStatus(in);
        String currentLine;

        System.out.println("Server: " + serverResponse.getStatusCode() + " " + serverResponse.getDetails());
        if(serverResponse.getStatusCode() == 111)
        {
            try{
                currentLine = in.readLine();
                System.out.println("Server: " + currentLine);
            } catch (Exception e){
                throw new DictConnectionException();
            }
            while (!(currentLine.equals("."))){
                String [] result = DictStringParser.splitAtoms(currentLine);
                MatchingStrategy stratEntry = new MatchingStrategy(result[0], result[1]);
                set.add(stratEntry);
                try{
                    currentLine = in.readLine();
                    System.out.println("Server: " + currentLine);
                } catch (Exception e){
                    throw new DictConnectionException();
                }
            }
            serverResponse = Status.readStatus(in);
            System.out.println("Server: " + serverResponse.getStatusCode() + " " + serverResponse.getDetails());
            if(serverResponse.getStatusCode() != 250)
            {
                throw new DictConnectionException();
            }   
        }
        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server, or an empty set if no matches were found.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected
     * value, or the database or strategy are invalid.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database)
            throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        // TODO Add your code here
        // Logs/Debug for Sanity
        // String [] temp = DictStringParser.splitAtoms(word);
        // for (String string : temp) {
        // System.out.print(string + " ");
        // }
        // System.out.println(temp.length);

        out.println("MATCH " + database.getName() + " " + strategy.getName() + " \"" + word + "\"");
        System.out.println("Client: MATCH " + database.getName() + " " + strategy.getName() + " " + word);

        Status serverResponse = Status.readStatus(in); // Response code and init message
        String currentLine;

        System.out.println("Server: " + serverResponse.getStatusCode() + " " + serverResponse.getDetails());
        if (serverResponse.getStatusCode() == 152) {
            try {
                currentLine = in.readLine(); // The first result
                String[] result = DictStringParser.splitAtoms(currentLine);
                set.add(result[1]); // Add result
                System.out.println("Server : " + currentLine);
                // System.out.println("My shitty array: " + result[1]);
            } catch (Exception e) {
                throw new DictConnectionException();
            }

            while (!(currentLine.equals("."))) {
                try {
                    currentLine = in.readLine();
                    System.out.println("Server : " + currentLine);
                } catch (Exception e) {
                    // TODO: handle exception
                    throw new DictConnectionException();
                }
                if (!(currentLine.equals("."))) {
                    String[] result = DictStringParser.splitAtoms(currentLine);
                    set.add(result[1]);
                }
            }
            serverResponse = Status.readStatus(in);
            System.out.println("Server: " + serverResponse.getStatusCode() + " " + serverResponse.getDetails());
            if (serverResponse.getStatusCode() != 250) {
                throw new DictConnectionException();
            }
        } else if (serverResponse.getStatusCode() == 550 || serverResponse.getStatusCode() == 551) {
            throw new DictConnectionException();
        }
        // TODO handle code 552????

        return set;
    }
    
    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server, or an empty
     * collection if no definitions were returned.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected
     * value, or the database is invalid.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database)
            throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        // TODO Add your code here
        word = word.trim();
        out.println("DEFINE " + database.getName() + " \"" + word + "\"");
        System.out.println("Client: DEFINE " + database.getName() + " " + word);

        Status serverResponse = Status.readStatus(in); // if positive reply then : 150 .......

        if (serverResponse.getStatusCode() == 150) {
            String currentLine = serverResponse.getDetails(); // collecting number of definitions found
            String[] result = DictStringParser.splitAtoms(currentLine);
            int numDefs = Integer.parseInt(result[0]); // parsing number of definitions found

            System.out.println("Server  :" + serverResponse.getStatusCode() + " " + serverResponse.getDetails()); // printing
                                                                                                                  // :
                                                                                                                  // 150
                                                                                                                  // .....
            try {
                currentLine = in.readLine();// reading the first definition

            } catch (Exception e) {
                throw new DictConnectionException();
            }
            String[] result2 = DictStringParser.splitAtoms(currentLine); // splitting word database name and defintion
            Definition def = new Definition(result2[1], result2[2]); // initializing definition with word and database
                                                                     // name
            while ((!(currentLine.equals("."))) || numDefs > 0) {

                System.out.println("Server : " + currentLine);
                try {
                    currentLine = in.readLine();
                    if (!(currentLine.equals("."))) {
                        def.appendDefinition(currentLine);//appends defintions to member defintion
                    }
                } catch (Exception e) {
                    throw new DictConnectionException();
                }
                if (currentLine.equals(".") && numDefs > 0) {
                    System.out.println("Server : " + currentLine);
                    set.add(def);
                    if (numDefs != 1) {
                        try {
                            currentLine = in.readLine();
                            result2 = DictStringParser.splitAtoms(currentLine);
                            def = new Definition(result2[1], result2[2]);
                        } catch (Exception e) {
                            // TODO: handle exception
                            throw new DictConnectionException();
                        }
                    }
                    numDefs--;
                }
            }
            serverResponse = Status.readStatus(in);
            if (serverResponse.getStatusCode() != 250) {
                throw new DictConnectionException();
            }
            System.out.println("Server: " + serverResponse.getStatusCode() + " " + serverResponse.getDetails());
        } else if (serverResponse.getStatusCode() == 550 || serverResponse.getStatusCode() == 552) {
            throw new DictConnectionException();
        }
        return set;
    }

}
