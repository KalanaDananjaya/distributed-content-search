package com.distributed.p2pFileTransfer;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

class QueryListener implements Runnable {
  private final AbstractFileTransferService fileTransferService;
  private ExecutorService executorService;
  private DatagramSocket socket;
  private boolean terminate = false;
  private final HashMap<Node, List<Executor>> pendingExecutors;
  private Logger logger;
  private long queryCount = 0;
  private final Set<String> pendingSearchQueries = ConcurrentHashMap.newKeySet();

  public QueryListener(AbstractFileTransferService fileTransferService, int port)
      throws SocketException {
    this.fileTransferService = fileTransferService;
    executorService = Executors.newCachedThreadPool();
    pendingExecutors = new HashMap<>();
    socket = new DatagramSocket(null);
    socket.setReuseAddress(true);
    socket.bind(new InetSocketAddress(port));
    socket.setSoTimeout(1000);
    logger = Logger.getLogger(this.getClass().getName());
  }

  public DatagramSocket getSocket() {
    return socket;
  }

  @Override
  public void run() {
    while (!terminate) {
      byte[] buffer = new byte[65536];
      DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
      try {
        socket.receive(incoming);
        queryCount++;
        String message = new String(buffer).split("\0")[0];
        Node origin = new Node(incoming.getAddress(), incoming.getPort());
        executorService.submit(new ListenerThread(message, origin));
      } catch (SocketTimeoutException e) {
        logger.log(Level.FINE, "Listener timeout");
      } catch (IOException e) {
        throw new RuntimeException("IO exception in socket listener");
      }
    }
    while (socket.isBound()) {
      socket.disconnect();
      socket.close();
    }
  }

  /**
   * Used by executors to tell query listener to notify them when a message is received from a node
   *
   * @param node node from which executor is expecting a message
   * @param executor who is expecting the message
   */
  public void registerForResponse(Node node, Executor executor) {
    synchronized (pendingExecutors) {
      if (pendingExecutors.containsKey(node)) {
        pendingExecutors.get(node).add(executor);
      } else {
        pendingExecutors.put(node, new LinkedList<>(Collections.singletonList(executor)));
      }
    }
  }

  /**
   * Used by executors to unregister from future responses from a node
   *
   * @param node node to which it previously registered to
   * @param executor who wants to stop notifications
   */
  public void unRegisterForResponse(Node node, Executor executor) {
    synchronized (pendingExecutors) {
      pendingExecutors.get(node).remove(executor);
    }
  }

  public void stop() {
    terminate = true;
  }

  public long getQueryCount() {
    return queryCount;
  }

  private class ListenerThread implements Runnable {
    String message;
    Node origin;
    FileHandler fileHandler;

    public ListenerThread(String message, Node origin) {
      this.message = message;
      this.origin = origin;
      fileHandler = fileTransferService.getFileHandler();
    }

    @Override
    public void run() {
      String[] data = message.split(" ");
      String queryType = data[1];
      switch (queryType) {
        case "SEROK":
        case "REGOK":
        case "JOINOK":
        case "LEAVEOK":
          synchronized (pendingExecutors) {
            List<Executor> executors = new LinkedList<>(pendingExecutors.get(origin));
            for (Executor executor : executors) {
              executor.notify(message);
            }
          }
          break;
        case "SER":
          String fileName = data[4].replaceAll("\"", "").replaceAll("_", " ");
          UUID uuid = UUID.fromString(data[5]);
          FileSearchRunner fileSearchRunner = new FileSearchRunner(fileName, origin, uuid);
          executorService.execute(fileSearchRunner);
          break;
        case "JOIN":
          try {
            Node node = new Node(InetAddress.getByName(data[2]), Integer.parseInt(data[3]));
            JoinRunner joinRunner = new JoinRunner(node);
            executorService.execute(joinRunner);
          } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, e.toString());
          }
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + queryType);
      }
    }
  }

  private class FileSearchRunner implements Runnable {
    String searchQuery;
    Node sender;
    UUID queryId;

    public FileSearchRunner(String searchQuery, Node sender, UUID queryId) {
      this.searchQuery = searchQuery;
      this.sender = sender;
      this.queryId = queryId;
    }

    @Override
    public void run() {
      Query responseQuery;
      if (pendingSearchQueries.contains(searchQuery)){
        String body = fileTransferService.getCommandBuilder().getSearchOkCommand(Collections.singletonList("<ignore>"), queryId);
        responseQuery = Query.createQuery(body, sender);
        try {
          QueryResult result =
                  fileTransferService.getQueryDispatcher().dispatchOne(responseQuery).get();
          logger.log(
                  Level.INFO,
                  String.format(
                          "response %s send to message id %s", responseQuery.body, queryId.toString()));
        } catch (InterruptedException | ExecutionException e) {
          e.printStackTrace();
        }
        return;
      }
      pendingSearchQueries.add(searchQuery);
      FileHandler fileHandler = fileTransferService.getFileHandler();
      List<String> files = fileHandler.searchForFile(searchQuery);
      try {
        List<String> neighbourFiles = fileTransferService.searchForFileSkippingSource(searchQuery,sender).get();
        pendingSearchQueries.remove(searchQuery);
        for (String file : neighbourFiles) {
           if(!file.equals("<ignore>") && !files.contains(file)){
             files.add(file);
           }
        }
      } catch (InterruptedException | ExecutionException e) {
        logger.log(Level.SEVERE, e.toString());
      }
      String body = fileTransferService.getCommandBuilder().getSearchOkCommand(files, queryId);
      responseQuery = Query.createQuery(body, sender);
      try {
        QueryResult result =
            fileTransferService.getQueryDispatcher().dispatchOne(responseQuery).get();
        logger.log(
            Level.INFO,
            String.format(
                "response %s send to message id %s", responseQuery.body, queryId.toString()));
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }
  }

  private class JoinRunner implements Runnable {
    Node other;

    public JoinRunner(Node other) {
      this.other = other;
    }

    @Override
    public void run() {
      fileTransferService.getNetwork().addNeighbour(other);
      Query joinOk =
          Query.createQuery(fileTransferService.getCommandBuilder().getJoinOkCommand(), other);
      try {
        fileTransferService.getQueryDispatcher().dispatchOne(joinOk).get();
        logger.log(Level.INFO, String.format("join ok to node %s", other.toString()));
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }
  }
}
