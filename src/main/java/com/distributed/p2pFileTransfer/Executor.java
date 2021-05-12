package com.distributed.p2pFileTransfer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public abstract class Executor implements Callable<QueryResult> {
  final Logger logger;
  Query query;
  String message;
  Node destination;
  DatagramSocket socket;
  QueryListener queryListener;

  public Executor(Query query, DatagramSocket socket, QueryListener queryListener) {
    this.query = query;
    this.socket = socket;
    this.message = query.body;
    this.destination = query.destination;
    this.queryListener = queryListener;
    logger = Logger.getLogger(Executor.class.getName());
  }

  public abstract void notify(String message);
}

class AcknowledgedQueryExecutor extends Executor {

  private final Object monitor = new Object();
  private String response;
  private boolean responseReceived = false;
  private String expectedResponseHeader;

  public AcknowledgedQueryExecutor(
      Query query, DatagramSocket socket, QueryListener queryListener) {
    super(query, socket, queryListener);
    expectedResponseHeader = String.format("%sOK",query.body.split(" ")[1]);
    if(expectedResponseHeader.equals("UNREGOK")){
      expectedResponseHeader = "UNROK"; // change this to UNREGOK if needed
    }
  }

  @Override
  public void notify(String message) {
    String responseHeader = message.split(" ")[1];
    if (!responseHeader.equals(expectedResponseHeader)){
      return;
    }
    response = message;
    responseReceived = true;
    logger.log(Level.INFO, String.format("Message received %s for query %s", message, query.id));
    synchronized (monitor) {
      monitor.notifyAll();
    }
    queryListener.unRegisterForResponse(destination, this);
  }

  @Override
  public QueryResult call() {
    byte[] data = query.body.getBytes(StandardCharsets.UTF_8);
    DatagramPacket sendDatagram =
        new DatagramPacket(data, data.length, query.destination.getSocketAddress());
    queryListener.registerForResponse(query.destination, this);
    try {
      socket.send(sendDatagram);
    } catch (IOException e) {
      return new QueryResult(String.format("Failed to send message due to : %s", e), 1, query);
    }
    while (!responseReceived) {
      synchronized (monitor) {
        try {
          monitor.wait();
        } catch (InterruptedException e) {
          return new QueryResult(String.format("Failed to send message due to : %s", e), 2, query);
        }
      }
    }
    logger.log(Level.INFO, String.format("Query %s handled successfully", query.id));
    return new QueryResult(response, 0, query);
  }
}

class FileSearchQueryExecutor extends AcknowledgedQueryExecutor {
  FileHandler fileHandler;
  String fileName;

  public FileSearchQueryExecutor(
      Query query, DatagramSocket socket, QueryListener queryListener, FileHandler fileHandler) {
    super(query, socket, queryListener);
    this.fileHandler = fileHandler;
    this.fileName = query.body.split(" ")[4].replaceAll("\"", "");
  }

  @Override
  public void notify(String message) {
    String[] data = message.split(" ");
    String command = data[1];
    String id = data[5];
    if (command.equals("SEROK") && id.equals(query.id.toString())) {
      int numberOfFiles = Integer.parseInt(data[2]);
      if (numberOfFiles > 0) {
        try {
          Node source = new Node(InetAddress.getByName(data[3]), Integer.parseInt(data[4]));
          Stream.of(data)
              .skip(6)
              .forEach(
                  fileName -> {
                    try {
                      Future<FileDownloadResult> future =  fileHandler.downloadFileToCache(source, fileName.replaceAll("_", " "));
                      if (fileName.equals(this.fileName)){
                        future.get();
                      }
                    } catch (NullPointerException ignored) {

                    } catch (InterruptedException | ExecutionException e) {
                      logger.log(Level.SEVERE, e.toString());
                    }
                  });
        } catch (UnknownHostException e) {
          logger.log(
              Level.WARNING,
              String.format("Failed to get Inet address ipAddress: %s port: %s", data[3], data[4]));
        }
      }
      super.notify(message);
    }
  }

  @Override
  public QueryResult call() {
    QueryResult result = super.call();
    return result;
  }
}

class UnAcknowledgedQueryExecutor extends Executor {
  Logger logger;

  public UnAcknowledgedQueryExecutor(
      Query query, DatagramSocket socket, QueryListener queryListener) {
    super(query, socket, queryListener);
  }

  @Override
  public void notify(String message) {
    logger.log(
        Level.WARNING,
        String.format(
            "Unacknowledged query executor message received Message received %s", message));
  }

  @Override
  public QueryResult call() {
    byte[] data = query.body.getBytes(StandardCharsets.UTF_8);
    DatagramPacket sendDatagram =
        new DatagramPacket(data, data.length, query.destination.getSocketAddress());
    try {
      socket.send(sendDatagram);
    } catch (IOException e) {
      return new QueryResult(String.format("Failed to send message due to : %s", e), 1, query);
    }
    return new QueryResult("Message send successfully", 0, query);
  }
}
