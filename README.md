# sensorthings-provisioning
The SensorThings Provisioning Service is supposed to make it easier to set up a SensorThings server (e.g. [GOST](https://github.com/gost/server)) for an IoT landscape. It allows the maintenance of all metadata in the SensorThings server using a single JSON object while the value in mandatory field `name` is used as a unique identifier for Things, Locations, Datastreams, Sensors and ObservedProperties. Objects of the same kind with the same name will only be created once. Existing objects (identified via their  `name`  field) will be patched. The service will not delete any objects already existing in GOST server.

In addition, the service allows the definition of statements to be created in a [LinkSmart Data Processing Agent](https://docs.linksmart.eu/display/LA)  so that the results of the statements are being published to the SensorThings server via an MQTT broker. A possible use case for this is the conversion of data being published by a [LinkSmart Device Gateway](https://docs.linksmart.eu/display/DGW/Device+Gateway)  in SenML format into OGC format. A statement can be defined for each Datastream in the `properties`  field of the Datastream´s corresponding Thing (see example below). The Datastream and the statement property need to have the same name.

## Deployment

Use the following `docker-compose`  file to set up an exemplary deployment using the public  [LinkSmart demo broker](https://docs.linksmart.eu/display/HOME/LinkSmart+Demo):

```yaml
version: '3.5'
services:
  gost-db:
    image: geodan/gost-db
    container_name: "gost-db"
    environment:
      POSTGRES_DB: gost
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
  gost:
    image: geodan/gost
    container_name: "gost"
    environment:
      GOST_DB_HOST: gost-db
      GOST_SERVER_EXTERNAL_URI: http://<address_of_your_host>:8080/
      GOST_MQTT_HOST: demo.linksmart.eu
      GOST_MQTT_PORT: 8883
      GOST_MQTT_SSL: "true"
      GOST_MQTT_CA_CERT_FILE: /etc/ssl/cert.pem
      GOST_MQTT_USERNAME: linksmart
      GOST_MQTT_PASSWORD: demo
      GOST_MQTT_CLIENTID: gost_linksmart
      GOST_SERVER_MAX_ENTITIES: 1000
      GOST_LOG_VERBOSE_FLAG: "true"
  dashboard:
    image: geodan/gost-dashboard
    container_name: "dashboard"
    ports:
      - 8080:8080
  sensorthings-provisioning:
    image: linksmart/sensorthings-provisioning
    container_name: "sensorthings-provisioning"
    ports:
      - 8081:8080
    environment:
      GOST_BASE_URI: http://dashboard:8080/
      AGENT_BASE_URI: http://agent:8319/
  agent:
    image: linksmart/dpa:snapshot
    container_name: "agent"
    ports:
      - 8319:8319
    environment:
      - agent_id=linksmart
      - connection_broker_mqtt_hostname=demo.linksmart.eu
      - connection_broker_mqtt_port=8883
      - connection_broker_mqtt_security_certificateBaseSecurityEnabled=true
      - messaging_client_mqtt_security_user=linksmart
      - messaging_client_mqtt_security_password=demo
      - "api_events_mqtt_topic_incoming_SenML=LS/v2/DGW/#"
```

The provisioning service can be accessed on port 8081 at endpoint `/provisioning/`. The POST body needs to be a list of Things in the format defined for the request body of ["Post Thing ULTIMATE"](https://gost1.docs.apiary.io/#reference/0/things/post-thing-ultimate) requests to GOST (Observations are currently not handled). See here for an example setting up `linksmart-black` and `linksmart-apple` with their sensors in GOST and converting the SenML data into OGC format using the Data Processing Agent:

```json
{
  "Things": [
    {
      "name": "linksmart-black",
      "description": "Raspberry Pi",
      "properties": {
        "organisation": "FIT",
        "owner": "Jannis Warnat",
        "linksmart-black-temperature": {
          "statement": "select e[0].v as result from SenML(bn=\"linksmart-black/\")"
        },
        "linksmart-black-humidity": {
          "statement": "select e[1].v as result from SenML(bn=\"linksmart-black/\")"
        }
      },
      "Locations": [
        {
          "name": "C5.110",
          "description": "Office Jannis Warnat",
          "encodingType": "application/vnd.geo+json",
          "location": {
            "coordinates": [
              7.203599523171596,
              50.74929555
            ],
            "type": "Point"
          }
        }
      ],
      "Datastreams": [
        {
          "name": "linksmart-black-temperature",
          "unitOfMeasurement": {
            "name": "Celsius",
            "symbol": "C",
            "definition": "http://www.qudt.org/qudt/owl/1.0.0/unit/Instances.html#Celsius"
          },
          "description": "Temperature measurement from linksmart-black",
          "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
          "ObservedProperty": {
            "name": "Temperature",
            "description": "Temperature in situ",
            "definition": "http://www.qudt.org/qudt/owl/1.0.0/quantity/Instances.html#Temperature"
          },
          "Sensor": {
            "name": "DHT22",
            "description": "Digital temperature and humidity sensor",
            "encodingType": "application/pdf",
            "metadata": "https://www.sparkfun.com/datasheets/Sensors/Temperature/DHT22.pdf"
          }
        },
        {
          "name": "linksmart-black-humidity",
          "unitOfMeasurement": {
            "name": "Percentage",
            "symbol": "%",
            "definition": "https://www.quora.com/What-is-the-unit-of-humidity"
          },
          "description": "Humidity measurement from linksmart-black",
          "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
          "ObservedProperty": {
            "name": "Relative humidity",
            "description": "Relative humidity in situ",
            "definition": "https://en.wikipedia.org/wiki/Relative_humidity"
          },
          "Sensor": {
            "name": "DHT22",
            "description": "Digital temperature and humidity sensor",
            "encodingType": "application/pdf",
            "metadata": "https://www.sparkfun.com/datasheets/Sensors/Temperature/DHT22.pdf"
          }
        }
      ]
    },
    {
      "name": "linksmart-apple",
      "description": "Raspberry Pi",
      "properties": {
        "organisation": "FIT",
        "owner": "Farshid Tavakolizadeh",
        "linksmart-apple-temperature": {
          "statement": "select e[0].v as result from SenML(bn=\"linksmart-apple/\")"
        },
        "linksmart-apple-humidity": {
          "statement": "select e[1].v as result from SenML(bn=\"linksmart-apple/\")"
        }
      },
      "Locations": [
        {
          "name": "C5.140",
          "description": "Middle Room",
          "encodingType": "application/vnd.geo+json",
          "location": {
            "coordinates": [
              7.203599523171596,
              50.74929555
            ],
            "type": "Point"
          }
        }
      ],
      "Datastreams": [
        {
          "name": "linksmart-apple-temperature",
          "unitOfMeasurement": {
            "name": "Celsius",
            "symbol": "C",
            "definition": "http://www.qudt.org/qudt/owl/1.0.0/unit/Instances.html#Celsius"
          },
          "description": "Temperature measurement from linksmart-apple",
          "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
          "ObservedProperty": {
            "name": "Temperature",
            "description": "Temperature in situ",
            "definition": "http://www.qudt.org/qudt/owl/1.0.0/quantity/Instances.html#Temperature"
          },
          "Sensor": {
            "name": "DHT22",
            "description": "Digital temperature and humidity sensor",
            "encodingType": "application/pdf",
            "metadata": "https://www.sparkfun.com/datasheets/Sensors/Temperature/DHT22.pdf"
          }
        },
        {
          "name": "linksmart-apple-humidity",
          "unitOfMeasurement": {
            "name": "Percentage",
            "symbol": "%",
            "definition": "https://www.quora.com/What-is-the-unit-of-humidity"
          },
          "description": "Humidity measurement from linksmart-apple",
          "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
          "ObservedProperty": {
            "name": "Relative humidity",
            "description": "Relative humidity in situ",
            "definition": "https://en.wikipedia.org/wiki/Relative_humidity"
          },
          "Sensor": {
            "name": "DHT22",
            "description": "Digital temperature and humidity sensor",
            "encodingType": "application/pdf",
            "metadata": "https://www.sparkfun.com/datasheets/Sensors/Temperature/DHT22.pdf"
          }
        }
      ]
    }
  ]
}
```

Notice that the data from `linksmart-black`  and `linksmart-apple` is appearing as observations in the GOST server.
