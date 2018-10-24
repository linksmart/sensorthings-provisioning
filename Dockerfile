FROM openjdk:10-jre

# mounting configuration and extra dependencies volumes
ADD target/ProvisionGost-1.0-SNAPSHOT.jar ProvisionGost.jar

# starting the agent
ENTRYPOINT ["java","-jar", "ProvisionGost.jar"]