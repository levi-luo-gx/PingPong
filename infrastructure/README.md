
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
