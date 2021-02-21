from pathlib import Path
import random
import time
from subprocess import call

nodeCount = 10
runningNodes = 10
sleepTime = 15
storagePath = "/home/thameera/Distributed/python"
nodePropertiesPath = ""
port = 1235
content = [ "Adventures of Tintin", "Jack and Jill", "Glee", "The Vampire Diarie", "King Arthur", "Windows XP", "Harry Potter", "Kung Fu Panda", "Lady Gaga", "Twilight", "Windows 8", "Mission Impossible", "Turn Up The Music", "Super Mario", "American Pickers", "Microsoft Office 2010", "Happy Feet", "Modern Family", "American Idol", "Hacking for Dummies"]

def createNodes(nodeCount, storagePath, nodePropertiesPath, port, content):
    for i in range(nodeCount):
        i+=1
        Path(storagePath + "/node%s/local_storage"%i).mkdir(parents=True, exist_ok=True)
        Path(storagePath + "/node%s/cache_storage"%i).mkdir(parents=True, exist_ok=True)
        with open(nodePropertiesPath + "node%s.properties" % i, "w") as f:
            f.write("cache_dir=%s/node%s/cache_storage\nlocal_dir=%s/node%s/local_storage\ncache_size=10000000\nport=%s\nboostrap_server_ip=127.0.0.1\nboostrap_server_port=55555" % (storagePath, i, storagePath, i, i+port))
        with open(storagePath + "/node%s/local_storage/filelist.txt"%i, "w") as f:
            randomContent = random.sample(content, random.randrange(len(content)))
            for k in randomContent:
                f.write("%s\n"%k)
        open(storagePath + "/node%s/cache_storage/filelist.txt"%i, "w")

def runNodes(runningNodes, sleepTime):
    for i in range(runningNodes):
        i+=1
        call(['gnome-terminal', '--tab', '-e', 'java -jar p2pFileTransfer-0.0.1-SNAPSHOT.jar node%s.properties'%i])
        time.sleep(sleepTime)

createNodes(nodeCount, storagePath, nodePropertiesPath, port, content)
runNodes(runningNodes, sleepTime)
