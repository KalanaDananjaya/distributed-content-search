# [Distributed Content Search](https://github.com/KalanaDananjaya/distributed-content-search)
## How to build the project
Give exectution permission to the maven wrapper using command
```bash
    chmod +x ./mvnw
```
### Using maven wrapper
To compile the project in to a single jar file use the command
```bash
    ./mvnw package
```
### Using the makefile
To compile the project in to a single jar file use the command
```bash
    make
```
This still internally calls a maven build skipping tests. Therefore you still need to give execution permission to maven wrapper

## How to run the project
After successfully building the project you will get the client in  ```target/p2pFileTransfer-0.0.1-SNAPSHOT.jar```.
You can run the jar file using the command
```bash
    java -jar p2pFileTransfer-0.0.1-SNAPSHOT.jar <path-to-your-config-file>
```
This will start running a client instance based on the cofiguration provided
### Configuration file
```
cache_dir=<path to cache>
local_dir=<path to file storage>
cache_size=<maximum number of files to cache>
port=<server on which the node will run>
boostrap_server_ip=127.0.0.1
boostrap_server_port=55555

```

We have included some example configurations in test_data directory

## Clean the build artifacts
Use either
```bash
    ./mvnw clean
```
or
```bash
    make clean
```