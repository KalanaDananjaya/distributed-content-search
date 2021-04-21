package com.distributed.p2pFileTransfer;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public abstract class AbstractFileTransferService {
  private final Network network;
  private final FileHandler fileHandler;
  private final QueryDispatcher queryDispatcher;
  private final QueryListener queryListener;
  private final CommandBuilder commandBuilder;
  private Node currentNode;
  private Thread queryListenerThread;
  protected  Logger logger;

  public AbstractFileTransferService(FileHandler fileHandler, int port, Node bootstrapServer)
      throws SocketException, UnknownHostException, NodeNotFoundException {
    this.fileHandler = fileHandler;
    this.queryListener = new QueryListener(this, port);
    this.queryDispatcher = new QueryDispatcher(this);
    this.currentNode = new Node(port);
    this.commandBuilder = CommandBuilder.getInstance(currentNode);
    queryListenerThread = new Thread(this.queryListener);
    queryListenerThread.start();
    this.network = new Network(this, bootstrapServer);
    logger = Logger.getLogger(this.getClass().getName());
    setLoggers(Paths.get(""));
  }

  private void setLoggers(Path logDirectory) {
    Path logFilePath = Paths.get(logDirectory.toString(), "log");
    List<Logger> loggers = new LinkedList<>();
    loggers.add(logger);
    loggers.add(Logger.getLogger(this.fileHandler.getClass().getName()));
    loggers.add(Logger.getLogger(this.queryListener.getClass().getName()));
    loggers.add(Logger.getLogger(this.queryDispatcher.getClass().getName()));
    loggers.add(Logger.getLogger(this.network.getClass().getName()));
    loggers.add(Logger.getLogger(Executor.class.getName()));
    try {
      java.util.logging.FileHandler fileHandler =
          new java.util.logging.FileHandler(logFilePath.toString());
      loggers.stream()
          .forEach(
              logger -> {
                logger.addHandler(fileHandler);
                SimpleFormatter formatter = new SimpleFormatter();
                fileHandler.setFormatter(formatter);
                logger.setUseParentHandlers(false);
              });
    } catch (IOException e) {
      System.out.println("Unable to setup file handler to log due to " + e.toString());
    }
  }

  /**
   * Used to search for a file. This will flood the network and return all matching <b>unique</b>
   * files. File is considered unique based on the file name
   *
   * @param query what to search for
   * @return list of files matching the search query
   */
  public abstract Future<List<String>> searchForFile(String query);

  protected abstract Future<List<String>> searchForFileSkippingSource(String query, Node source);

  /**
   * Use to flood the network with a query and get the response
   *
   * @param queryBody Body of the query
   * @return results of flooding the network
   */
  protected abstract Future<List<QueryResult>> floodNetwork(String queryBody);

  /**
   * Used to download a file. Will download the first file that exactly matches the file name.
   *
   * @param fileName Name of the file to download
   * @throws DestinationAlreadyExistsException If the destination file already exists. <b>This
   *     method will not overwrite existing files</b>
   * @return
   */
  public abstract Future<FileDownloadResult> downloadFile(String fileName)
      throws  DestinationAlreadyExistsException;

  /**
   * Used to directly download a file without search for it in the network
   *
   * @param fileName Name of the file to download
   * @param source Node from which to download the file
   * @throws DestinationAlreadyExistsException If the destination file already exists. <b>This
   *     method will not overwrite existing files</b>
   * @throws NodeNotFoundException If source node refused to connect
   * @return
   */
  public abstract Future<FileDownloadResult> downloadFileFrom(String fileName, Node source)
      throws DestinationAlreadyExistsException, NodeNotFoundException;

  Network getNetwork() {
    return network;
  }

  FileHandler getFileHandler() {
    return fileHandler;
  }

  QueryDispatcher getQueryDispatcher() {
    return queryDispatcher;
  }

  QueryListener getQueryListener() {
    return queryListener;
  }

  CommandBuilder getCommandBuilder() {
    return commandBuilder;
  }

  /**
   * Used to gracefully shutdown the file transfer service
    * @return future that resolves when shutdown completed
   */
  public Future<QueryResult> shutdown(){
    return this.network.disconnect();
  }

  /**
   * Used to print the routing table
   */
  public void printRoutingTable(){
    this.network.printRoutingTable();
  }

  /**
   * Use to get the number of queries received
   * @return number of queries received
   */
  public long getNumberOfQueriesReceived(){
    return this.queryListener.getQueryCount();
  }

  /**
   * Use to get the number of queries send
   * @return number of queries send
   */
  public long getNumberOfQueriesDispatched(){
    return this.queryDispatcher.getDispatchedCount();
  }

  /**
   * Print the number of search queries answered so far
   * @return
   */
  public long getAnsweredQueryCount(){
    return this.queryListener.getAnsweredCount();
  }

  void stop() {
    queryListener.stop();
    try {
      queryListenerThread.join();
    } catch (InterruptedException e) {
      logger.log(Level.SEVERE, e.toString());
    }
  }
}
