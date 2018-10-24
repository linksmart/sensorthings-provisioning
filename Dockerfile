FROM openjdk:10-jre

# mounting configuration and extra dependencies volumes
ADD target/ProvisionSensorThings-1.0-SNAPSHOT.jar ProvisionSensorThings.jar

# starting the agent
ENTRYPOINT ["java","-jar", "ProvisionSensorThings.jar"]
