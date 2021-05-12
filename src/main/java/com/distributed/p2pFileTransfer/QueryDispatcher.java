package com.distributed.p2pFileTransfer;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class QueryDispatcher {
  private final AbstractFileTransferService fileTransferService;
  private DatagramSocket socket;
  private ExecutorService executorService;
  private Logger logger;
  private long dispatchedCount = 0;

  QueryDispatcher(AbstractFileTransferService fileTransferService)
      throws SocketException {
    this.fileTransferService = fileTransferService;
    socket = fileTransferService.getQueryListener().getSocket();
    executorService = Executors.newCachedThreadPool();
    logger = Logger.getLogger(this.getClass().getName());
  }

  private Executor getQueryExecutor(Query query){
    Executor executor;
    String queryType = query.body.split(" ")[1];
    switch (queryType) {
      case "SER":
        executor =
                new FileSearchQueryExecutor(
                        query,
                        socket,
                        fileTransferService.getQueryListener(),
                        fileTransferService.getFileHandler()
                );
        break;
      case "UNREG":
      case "JOIN":
      case "LEAVE":
      case "REG":
        executor =
                new AcknowledgedQueryExecutor(query, socket, fileTransferService.getQueryListener());
        break;
      case "SEROK":
      case "UNROK":
      case "JOINOK":
      case "LEAVEOK":
      case "REGOK":
        executor =
                new UnAcknowledgedQueryExecutor(query, socket, fileTransferService.getQueryListener());
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + queryType);
    }
    return executor;
  }

  /**
   * Used to dispatch a query to a single node
   *
   * @param query query to dispatch
   * @return result of query
   */
  Future<QueryResult> dispatchOne(Query query) {
    dispatchedCount++;
    Executor executor = getQueryExecutor(query);
    return executorService.submit(executor);
  }

  /**
   * Dispatch all the queries given and get the results for all of them
   *
   * @param queries queries to dispatch
   * @return results of all the queries
   */
  List<Future<QueryResult>> dispatchAll(List<Query> queries) {
    dispatchedCount += queries.size();
    return queries.stream().map(this::dispatchOne).collect(Collectors.toList());
  }

  /**
   * Used to dispatch one of a given set of queries. May dispatch all or a selected number. Use when
   * dispatching any one of the given queries is sufficient.
   *
   * @param queries queries to dispatch
   * @return result of any successful queries if any, else failure
   */
  Future<QueryResult> dispatchAny(List<Query> queries) {
    dispatchedCount += queries.size();
    List<Executor> executors = queries.stream().map(this::getQueryExecutor).collect(Collectors.toList());
    Callable<QueryResult> dispatchTack = () -> {
      QueryResult result = null;
      try {
        result = executorService.invokeAny(executors);
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
      return result;
    };
    return executorService.submit(dispatchTack);
  }

  public long getDispatchedCount() {
    return dispatchedCount;
  }
}
