# Reversi online
Final project of [Evolution Scala bootcamp](https://github.com/evolution-gaming/scala-bootcamp)

## Project Description
Multiplayer game of [Reversi](https://en.wikipedia.org/wiki/Reversi) done in Scala using:
- **Http4s** - Backend service
- **ReactJs** with **typescript** - Frontend
- **Websockets** - Client/server communication

## Setup for development
Run the backend service
```sh
sbt run
```
From `frontend` directory run
```sh
npm install
npm start
```
In your browser open
```sh
localhost:3000
```

## Running the app
From project root run the following command
```sh
docker compose up
```
and open in your browser
```sh
localhost:80
```
this will create the following docker containers
- Container running "fat jar" of the app containing production build
- Reverse proxy container to map port 80 to 8080 (to avoid running jar with root privileges for port 80)

##### Sbt assembly command
This project uses [Sbt assembly plugin](https://github.com/sbt/sbt-assembly) to build "fat jar" of the project. `build.sbt` contains task that builds production frontend and copies result to build target, this task is executed during assembly, so you just need to run
```sh
sbt assembly
```
and then to run the app
```sh
java -jar target/scala-2.13/reversi-app.jar
```