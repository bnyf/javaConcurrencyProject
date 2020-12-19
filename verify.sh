#!/bin/sh

javac -cp . ticketingsystem/Verify.java

java -cp . ticketingsystem/Verify > trace 
java -jar verify.jar trace
