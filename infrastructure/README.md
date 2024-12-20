#requirements
Imaging a simplified scenario where you are asked to implement 2 microservices called “ping” and “pong” respectively and integrates with each other as illustrated below – 

Both services must be implemented using Spring WebFlux and can be packaged & run as executable jar.
Both services are running locally in same device. The integration goes as simple as – Ping Service attempts to says “Hello” to Pong Service around every 1 secondand then Pong service should respond with “World”.
The Pong Service should be implemented with a Throttling Control limited with value 1, meaning that – 
For any given second, there is only 1 requests can be handled by it.
For those additional requests coming in the given second, Pong Service should return 429 status code.
Multiple Ping Services should be running as separate JVM Process with capability of Rate Limit Control across all processes with only 2 RPS (hint: consider using Java FileLock), meaning that - 
If all processes attempt to triggers Pong Service at the same time, only 2 requests are allowed to go out to Pong Service.
Among the 2 outgoing requests to Pong, if they reach Pong Service within the same second, one of them are expected to be throttled with 429 status code.
Each Ping service process must log the request attempt with result (*)  in separate logs per instance for review. The result includes:
Request sent & Pong Respond.
Request not send as being “rate limited”.
Request send & Pong throttled it.
Increase the number of running Ping processes locally and review the logs for each.

#deploy
cd C:\Users\levig\Desktop\Projects\PingPong\infrastructure\
java -jar .\build\libs\ping-1.0.0.jar --server.port=8081
minikube start
kubectl create namespace app
docker run -d --name mongodb -p 27017:27017 mongo

#deploy pong
docker build -f Dockerfile_pong -t pong-image:1.0.1 .
minikube image load pong-image:1.0.1
kubectl apply -f ./infrastructure/k8s/pong.yaml

#deploy ping
docker build -f Dockerfile_ping -t ping-image:1.0.1 .
minikube image load ping-image:1.0.1
kubectl apply -f ./infrastructure/k8s/ping.yaml

#deploy mongo
docker pull mongo:4.4.18
minikube image load mongo:4.4.18
kubectl apply -f ./infrastructure/k8s/mongo.yaml

#expose mongo
kubectl port-forward svc/mongodb-service 28017:27017 -n app

#Test pong
clean test jacocoTestReport