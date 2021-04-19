target/p2pFileTransfer-0.0.1-SNAPSHOT.jar : src/main/java/com/distributed/p2pClient/Main.java mvnw
		./mvnw package -Dmaven.test.skip=true

clean:
		./mvnw clean
