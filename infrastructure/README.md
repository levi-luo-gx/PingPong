#demo progress
1.minikube start

2.kubectl create ns app

3.cd C:\Users\levig\Desktop\Projects\PingPong

4.#deploy mongo
docker pull mongo:4.4.18
minikube image load mongo:4.4.18
kubectl apply -f .\infrastructure\k8s\mongodb.yaml
kubectl port-forward service/mongodb-service 28017:27017 -n app

5.#deploy pong
update appliaction.properties
check build.gradle version
gradle clean build
docker build -f Dockerfile_pong -t pong-image:1.0.0 .
minikube image load pong-image:1.0.0
kubectl apply -f ./infrastructure/k8s/pong.yaml

6.#deploy ping
update appliaction.properties
check build.gradle version
gradle clean build
docker build -f Dockerfile_ping -t ping-image:1.0.1 .
minikube image load ping-image:1.0.1
kubectl apply -f ./infrastructure/k8s/ping.yaml

7.#log
kubectl logs -f --tail=200 pong-69688c76fb-7g5m7 -n app

8.#others
java -jar .\build\libs\ping-1.0.0.jar --server.port=8081
docker run -d --name mongodb -p 27017:27017 mongo
clean test jacocoTestReport
minikube ssh docker rmi <image_name_or_id>
kubectl scale deployment ping --replicas=20 -n app
