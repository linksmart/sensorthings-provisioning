package sensorthings.provisioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiKey;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.UnsupportedEncodingException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gostapi.GostClient;
import gostapi.Configuration;
import gostapi.controllers.APIController;
import gostapi.exceptions.APIException;
import gostapi.models.*;

import java.io.IOException;
import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@SpringBootApplication
@JsonInclude(NON_NULL)
@EnableSwagger2
public class ProvisionSensorThings {
    private static boolean retryStatements = false;

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    private String errorMessage = null;

    public String getAgentBaseURI() {
        return agentBaseURI;
    }

    public void setAgentBaseURI(String agentBaseUri) {
        this.agentBaseURI = agentBaseUri;
    }

    public String getGostBaseURI() {
        return gostBaseURI;
    }

    public void setGostBaseURI(String gostBaseUri) {
        this.gostBaseURI = gostBaseUri;
    }

    private String agentBaseURI = "undefined";
    private String gostBaseURI = null;
    private ObjectMapper mapper = new ObjectMapper();
    private ArrayNode statementsResponses = null;
    private ArrayNode locationsResponses = null;
    private ArrayNode thingsResponses = null;
    private ArrayNode datastreamsResponses = null;
    private ArrayNode observedPropertiesResponses = null;
    private ArrayNode sensorsResponses = null;
    private String contentType = "application/json";
    private APIController controller = new GostClient().getClient();
    private HttpCallBackCatcher httpResponse = new HttpCallBackCatcher();
    private String jsonString = null;
    private JsonNode thingTree = null;

    public ProvisionSensorThings() {
    }

    public ProvisionSensorThings(String jsonString) {

        try {
            thingTree = mapper.readTree(jsonString);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonNode gostBaseURINode = thingTree.get("gostBaseURI");
        if (gostBaseURINode != null) {
            setGostBaseURI(gostBaseURINode.asText());
            Configuration.baseUri = getGostBaseURI();
        } else {
            setErrorMessage("Make sure that gostBaseURI is given with request");
            throw new IllegalArgumentException(getErrorMessage());
        }

        JsonNode agentBaseURINode = thingTree.get("agentBaseURI");

        if (agentBaseURINode != null) {
            setAgentBaseURI(agentBaseURINode.asText());
        } else {
            retryStatements = false;
        }

        Iterator<JsonNode> things = thingTree.get("Things").elements();

        Map<String, JsonNode> existingLocations = new LinkedHashMap<String, JsonNode>();
        Map<String, JsonNode> existingDatastreams = new LinkedHashMap<String, JsonNode>();
        Map<String, JsonNode> existingSensors = new LinkedHashMap<String, JsonNode>();
        Map<String, JsonNode> existingObservedProperties = new LinkedHashMap<String, JsonNode>();
        Map<String, JsonNode> existingThings = new LinkedHashMap<String, JsonNode>();

        while (things.hasNext()) {
            JsonNode thing = things.next();
            existingThings.put(thing.get("name").asText(), getExistingThing(thing));
            JsonNode locations = thing.get("Locations");
            if (locations != null) {
                Iterator<JsonNode> iter = thing.get("Locations").elements();
                while (iter.hasNext()) {
                    JsonNode location = iter.next();
                    existingLocations.put(location.get("name").asText(), getExistingLocation(location));
                }
            }
            JsonNode datastreams = thing.get("Datastreams");
            if (datastreams != null) {
                Iterator<JsonNode> iter = thing.get("Datastreams").elements();
                while (iter.hasNext()) {
                    JsonNode datastream = iter.next();
                    existingDatastreams.put(datastream.get("name").asText(), getExistingDatastream(datastream));
                    JsonNode sensor = datastream.get("Sensor");
                    existingSensors.put(sensor.get("name").asText(), getExistingSensor(sensor));
                    JsonNode observedProperty = datastream.get("ObservedProperty");
                    existingObservedProperties.put(observedProperty.get("name").asText(), getExistingObservedProperty(observedProperty));
                }
            }
        }

        things = thingTree.get("Things").elements();

        while (things.hasNext()) {
            JsonNode thing = things.next();
            JsonNode propertiesNode = thing.get("properties");
            JsonNode locations = thing.get("Locations");
            List<Integer> locationIDs = new ArrayList<Integer>();
            if (locations != null) {

                Iterator<JsonNode> iter = thing.get("Locations").elements();

                while (iter.hasNext()) {
                    JsonNode location = iter.next();
                    locationIDs.add(postOrPatchLocation(location, existingLocations.get(location.get("name").asText())));
                }
            }
            int thingId = postOrPatchThing(thing, existingThings.get(thing.get("name").asText()), locationIDs);
            JsonNode datastreams = thing.get("Datastreams");

            if (datastreams != null) {
                Iterator<JsonNode> iter = thing.get("Datastreams").elements();
                int datastreamId = 0;
                //List<String> datastreamNames = new ArrayList<String>;
                while (iter.hasNext()) {
                    JsonNode datastream = iter.next();
                    JsonNode observedProperty = datastream.get("ObservedProperty");
                    int observedPropertyId = postOrPatchObservedProperty(observedProperty, existingObservedProperties.get(observedProperty.get("name").asText()));
                    JsonNode sensor = datastream.get("Sensor");
                    int sensorId = postOrPatchSensor(sensor, existingSensors.get(sensor.get("name").asText()));
                    String datastreamName = datastream.get("name").asText();
                    datastreamId = postOrPatchDatastream(datastream, existingDatastreams.get(datastream.get("name").asText()), thingId, observedPropertyId, sensorId);

                    if (propertiesNode != null) {

                        Iterator<String> propertiesFields = propertiesNode.fieldNames();

                        while (propertiesFields.hasNext()) {
                            String propertyName = propertiesFields.next();

                            if (propertyName.equals(datastreamName)) {
                                if (getAgentBaseURI() != null && !getAgentBaseURI().equals("undefined")) {
                                    JsonNode datastreamProperty = propertiesNode.get(datastreamName);
                                    String statement = datastreamProperty.get("statement").asText();
                                    ObjectNode postStatementJson = mapper.createObjectNode();
                                    postStatementJson.put("name", datastreamName);
                                    postStatementJson.set("statement", datastreamProperty.get("statement"));

                                    ArrayNode statementOutput = mapper.createArrayNode();
                                    String outputString = "GOST/Datastreams(" + datastreamId + ")/Observations";
                                    statementOutput.add(outputString);

                                    postStatementJson.set("output", statementOutput);
                                    try {
                                        putJsonUsingHttpClient(datastreamName, mapper.writeValueAsString(postStatementJson));
                                    } catch (JsonProcessingException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    retryStatements = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(ProvisionSensorThings.class, args);
    }

    @Bean
    public Docket provisionGostApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("sensorthings/provisioning"))
                .paths(PathSelectors.any())
                .build()
                ;
    }

    private ApiKey apiKey() {
        return new ApiKey("mykey", "api_key", "header");
    }

    private void addStatementsReponse(JsonNode node) {
        if (statementsResponses == null) {
            statementsResponses = mapper.createArrayNode();
        }
        statementsResponses.add(node);
    }

    private void addLocationsReponse(JsonNode node) {
        if (locationsResponses == null) {
            locationsResponses = mapper.createArrayNode();
        }
        locationsResponses.add(node);
    }

    private void addThingsReponse(JsonNode node) {
        if (thingsResponses == null) {
            thingsResponses = mapper.createArrayNode();
        }
        thingsResponses.add(node);
    }

    private void addDatastreamsReponse(JsonNode node) {
        if (datastreamsResponses == null) {
            datastreamsResponses = mapper.createArrayNode();
        }
        datastreamsResponses.add(node);
    }

    private void addObservedPropertiesReponse(JsonNode node) {
        if (observedPropertiesResponses == null) {
            observedPropertiesResponses = mapper.createArrayNode();
        }
        observedPropertiesResponses.add(node);
    }

    private void addSensorsReponse(JsonNode node) {
        if (sensorsResponses == null) {
            sensorsResponses = mapper.createArrayNode();
        }
        sensorsResponses.add(node);
    }

    @JsonGetter("Statements")
    public ArrayNode getStatementsResponses() {
        return statementsResponses;
    }

    @JsonSetter("Statements")
    public void setStatementsResponses(ArrayNode statements) {
        statementsResponses = statements;
    }

    @JsonGetter("Locations")
    public ArrayNode getLocationsResponses() {
        return locationsResponses;
    }

    @JsonSetter("Locations")
    public void setLocationsResponses(ArrayNode locations) {
        locationsResponses = locations;
    }

    @JsonGetter("Things")
    public ArrayNode getThingsResponses() {
        return thingsResponses;
    }

    @JsonSetter("Things")
    public void setThingsResponses(ArrayNode things) {
        thingsResponses = things;
    }

    @JsonGetter("Datastreams")
    public ArrayNode getDatastreamsResponses() {
        return datastreamsResponses;
    }

    @JsonSetter("Datastreams")
    public void setDatastreamsResponses(ArrayNode datastreams) {
        datastreamsResponses = datastreams;
    }

    @JsonGetter("ObservedProperties")
    public ArrayNode getObservedPropertiesResponses() {
        return observedPropertiesResponses;
    }

    @JsonSetter("ObservedProperties")
    public void setObservedPropertiesResponses(ArrayNode observedProperties) {
        this.observedPropertiesResponses = observedProperties;
    }

    @JsonGetter("Sensors")
    public ArrayNode getSensorsResponses() {
        return sensorsResponses;
    }

    @JsonSetter("Sensors")
    public void setSensorsResponses(ArrayNode sensors) {
        this.sensorsResponses = sensors;
    }

    public void putJsonUsingHttpClient(String name, String json) {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(getAgentBaseURI() + "/statement/" + name);

        StringEntity entity = null;
        try {
            entity = new StringEntity(json);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        httpPut.setEntity(entity);
        httpPut.setHeader("Accept", "application/json");
        httpPut.setHeader("Content-type", "application/json");

        CloseableHttpResponse response = null;
        try {
            response = client.execute(httpPut);
        } catch (IOException e) {
            retryStatements = true;
            e.printStackTrace();
        }
        if (response.getEntity() != null) {
            try {
                addStatementsReponse(mapper.readTree(response.getEntity().getContent()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JsonNode getExistingLocation(JsonNode location) {
        GetLocationsResponse getResponse = null;
        try {
            getResponse = controller.getLocations();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        JsonNode existingLocation = null;
        JsonNode locationName = location.get("name");
        JsonNode locationDescription = location.get("description");
        JsonNode locationEncodingType = location.get("encodingType");
        JsonNode locationLocation = location.get("location");

        if (locationName == null || locationDescription == null || locationEncodingType == null || locationLocation == null) {
            setErrorMessage("Location is lacking name, description, encodingType or location field.");
            throw new IllegalArgumentException(getErrorMessage());
        }

        int count = 0;
        for (Object element : getResponse.getValue()) {
            JsonNode locationElementName = mapper.valueToTree(element).get("name");
            if (locationElementName.equals(locationName)) {
                if (count > 0) {
                    setErrorMessage("There are multiple locations with the the name " + locationName + " registered in Gost server. Please clean this up.");
                    throw new IllegalStateException(getErrorMessage());
                }
                existingLocation = mapper.valueToTree(element);
                count++;
            }
        }
        return existingLocation;
    }

    private int postOrPatchLocation(JsonNode location, JsonNode existingLocation) {

        int locationId = 0;

        JsonNode locationName = location.get("name");
        JsonNode locationDescription = location.get("description");
        JsonNode locationEncodingType = location.get("encodingType");
        JsonNode locationLocation = location.get("location");

        if (existingLocation != null /*locationId > 0*/) {

            JsonNode existingDescription = existingLocation.get("description");
            JsonNode existingEncodingType = existingLocation.get("encodingType");
            JsonNode existingLocationLocation = existingLocation.get("location");
            int existingLocationId = existingLocation.get("@iot.id").asInt();
            locationId = existingLocationId;

            if (!locationDescription.equals(existingDescription) || !locationEncodingType.equals(existingEncodingType) || !locationLocation.equals(existingLocationLocation)) {
                PatchLocationRequest patchRequest = new PatchLocationRequest();
                patchRequest.setDescription(locationDescription.asText());
                patchRequest.setEncodingType(locationEncodingType.asText());
                patchRequest.setLocation(locationLocation);
                controller.setHttpCallBack(httpResponse);
                PatchLocationResponse patchResponse = null;
                try {
                    patchResponse = controller.patchLocation(existingLocationId, contentType, patchRequest);
                    addLocationsReponse(mapper.valueToTree(patchResponse));
                } catch (APIException e) {
                    try {
                        JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                        addLocationsReponse(httpResponse);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        } else {
            PostLocationRequest postRequest = new PostLocationRequest();
            postRequest.setName(locationName.asText());
            postRequest.setDescription(locationDescription.asText());
            postRequest.setEncodingType(locationEncodingType.asText());
            postRequest.setLocation(locationLocation);
            controller.setHttpCallBack(httpResponse);
            PostLocationResponse postRespose = null;
            try {
                postRespose = controller.postLocation(contentType, postRequest);
                addLocationsReponse(mapper.valueToTree(postRespose));
                locationId = postRespose.getMIotId();
            } catch (APIException e) {
                try {
                    JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                    addLocationsReponse(httpResponse);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        return locationId;
    }

    private JsonNode getExistingDatastream(JsonNode datastream) {
        GetDatastreamsResponse getResponse = null;
        try {
            getResponse = controller.getDatastreams();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        JsonNode existingDatastream = null;
        JsonNode datastreamName = datastream.get("name");
        JsonNode datastreamDescription = datastream.get("description");
        JsonNode datastreamObservationType = datastream.get("observationType");
        JsonNode unitOfMeasurement = datastream.get("unitOfMeasurement");

        if (datastreamName == null || datastreamDescription == null || datastreamObservationType == null || unitOfMeasurement == null) {
            setErrorMessage("Datastream is lacking name, description, observationType or unitOfMeasurement field.");
            throw new IllegalArgumentException(getErrorMessage());
        }

        int count = 0;
        for (Object element : getResponse.getValue()) {
            JsonNode datastreamElementName = mapper.valueToTree(element).get("name");
            if (datastreamElementName.equals(datastreamName)) {
                if (count > 0) {
                    setErrorMessage("There are multiple datastreams with the the name " + datastreamName + " registered in Gost server. Please clean this up.");
                    throw new IllegalStateException(getErrorMessage());
                }
                existingDatastream = mapper.valueToTree(element);
                count++;
            }
        }
        return existingDatastream;
    }

    private int postOrPatchDatastream(JsonNode datastream, JsonNode existingDatastream, int thingId, int observedPropertyId, int sensorId) {

        int datastreamId = 0;
        JsonNode datastreamName = datastream.get("name");
        JsonNode datastreamDescription = datastream.get("description");
        JsonNode datastreamObservationType = datastream.get("observationType");
        JsonNode unitOfMeasurement = datastream.get("unitOfMeasurement");
        ObjectNode datastreamUnitOfMeasurement = mapper.createObjectNode();
        datastreamUnitOfMeasurement.set("name", unitOfMeasurement.get("name"));
        datastreamUnitOfMeasurement.set("symbol", unitOfMeasurement.get("symbol"));
        datastreamUnitOfMeasurement.set("definition", unitOfMeasurement.get("definition"));
        ObjectNode datastreamThing = mapper.createObjectNode();
        datastreamThing.put("@iot.id", Integer.toString(thingId));
        ObjectNode datastreamObservedProperty = mapper.createObjectNode();
        datastreamObservedProperty.put("@iot.id", Integer.toString(observedPropertyId));
        ObjectNode datastreamSensor = mapper.createObjectNode();
        datastreamSensor.put("@iot.id", Integer.toString(sensorId));

        if (existingDatastream != null /*datastreamId > 0*/) {

            JsonNode existingDescription = existingDatastream.get("description");
            JsonNode existingObservationType = existingDatastream.get("observationType");
            JsonNode existingUnitOfMeasurement = existingDatastream.get("unitOfMeasurement");
            int existingDatastreamId = existingDatastream.get("@iot.id").asInt();
            datastreamId = existingDatastreamId;

            if (!datastreamDescription.equals(existingDescription) || !datastreamObservationType.equals(existingObservationType) || !unitOfMeasurement.equals(existingUnitOfMeasurement)) {
                PatchDatastreamRequest patchRequest = new PatchDatastreamRequest();
                patchRequest.setDescription(datastreamDescription.asText());
                patchRequest.setObservationType(datastreamObservationType.asText());
                patchRequest.setUnitOfMeasurement(datastreamUnitOfMeasurement);
                PatchDatastreamResponse patchResponse = null;
                controller.setHttpCallBack(httpResponse);
                try {
                    patchResponse = controller.patchDatastream(existingDatastreamId, contentType, patchRequest);
                    addDatastreamsReponse(mapper.valueToTree(patchResponse));
                } catch (APIException e) {
                    try {
                        JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                        addDatastreamsReponse(httpResponse);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        } else {
            PostDatastreamLinkedRequest postRequest = new PostDatastreamLinkedRequest();
            postRequest.setName(datastreamName.asText());
            postRequest.setDescription(datastreamDescription.asText());
            postRequest.setObservationType(datastreamObservationType.asText());
            postRequest.setUnitOfMeasurement(datastreamUnitOfMeasurement);
            postRequest.setThing(datastreamThing);
            postRequest.setObservedProperty(datastreamObservedProperty);
            postRequest.setSensor(datastreamSensor);
            PostDatastreamLinkedResponse postResponse = null;
            controller.setHttpCallBack(httpResponse);
            try {
                postResponse = controller.postDatastreamLinked(contentType, postRequest);
                addDatastreamsReponse(mapper.valueToTree(postResponse));
                datastreamId = postResponse.getMIotId();
            } catch (APIException e) {
                try {
                    JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                    addDatastreamsReponse(httpResponse);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        return datastreamId;
    }

    private JsonNode getExistingSensor(JsonNode sensor) {
        GetSensorsResponse getResponse = null;
        try {
            getResponse = controller.getSensors();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        JsonNode existingSensor = null;
        JsonNode sensorName = sensor.get("name");
        JsonNode sensorDescription = sensor.get("description");
        JsonNode sensorEncodingType = sensor.get("encodingType");
        JsonNode sensorMetadata = sensor.get("metadata");

        if (sensorName == null || sensorDescription == null || sensorEncodingType == null || sensorMetadata == null) {
            setErrorMessage("Sensor is lacking name, description, encodingType or metadata field.");
            throw new IllegalArgumentException(getErrorMessage());
        }

        int count = 0;
        for (Object element : getResponse.getValue()) {
            JsonNode sensorElementName = mapper.valueToTree(element).get("name");
            if (sensorElementName.equals(sensorName)) {
                if (count > 0) {
                    setErrorMessage("There are multiple sensors with the the name " + sensorName + " registered in Gost server. Please clean this up.");
                    throw new IllegalStateException(getErrorMessage());
                }
                existingSensor = mapper.valueToTree(element);
                count++;
            }
        }
        return existingSensor;
    }

    private int postOrPatchSensor(JsonNode sensor, JsonNode existingSensor) {

        int sensorId = 0;
        JsonNode sensorName = sensor.get("name");
        JsonNode sensorDescription = sensor.get("description");
        JsonNode sensorEncodingType = sensor.get("encodingType");
        JsonNode sensorMetadata = sensor.get("metadata");

        if (existingSensor != null /*sensorId > 0*/) {

            JsonNode existingDescription = existingSensor.get("description");
            JsonNode existingEncodingType = existingSensor.get("encodingType");
            JsonNode existingMetadata = existingSensor.get("metadata");
            int existingSensorId = existingSensor.get("@iot.id").asInt();
            sensorId = existingSensorId;

            if (!sensorDescription.equals(existingDescription) || !sensorEncodingType.equals(existingEncodingType) || !sensorMetadata.equals(existingMetadata)) {
                PatchSensorRequest patchRequest = new PatchSensorRequest();
                patchRequest.setDescription(sensorDescription.asText());
                patchRequest.setEncodingType(sensorEncodingType.asText());
                patchRequest.setMetadata(sensorMetadata.asText());
                PatchSensorResponse patchResponse = null;
                controller.setHttpCallBack(httpResponse);
                try {
                    patchResponse = controller.patchSensor(existingSensorId, contentType, patchRequest);
                    addSensorsReponse(mapper.valueToTree(patchResponse));
                } catch (APIException e) {
                    try {
                        JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                        addSensorsReponse(httpResponse);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        } else {
            PostSensorRequest postRequest = new PostSensorRequest();
            postRequest.setName(sensorName.asText());
            postRequest.setDescription(sensorDescription.asText());
            postRequest.setEncodingType(sensorEncodingType.asText());
            postRequest.setMetadata(sensorMetadata.asText());
            PostSensorResponse postResponse = null;
            controller.setHttpCallBack(httpResponse);
            try {
                postResponse = controller.postSensor(contentType, postRequest);
                addSensorsReponse(mapper.valueToTree(postResponse));
                sensorId = postResponse.getMIotId();
            } catch (APIException e) {
                try {
                    JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                    addSensorsReponse(httpResponse);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        return sensorId;
    }

    private JsonNode getExistingObservedProperty(JsonNode observedProperty) {
        GetObservedPropertiesResponse getResponse = null;
        try {
            getResponse = controller.getObservedProperties();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        JsonNode existingObservedProperty = null;
        JsonNode observedPropertyName = observedProperty.get("name");
        JsonNode observedPropertyDescription = observedProperty.get("description");
        JsonNode observedPropertyDefinition = observedProperty.get("definition");

        if (observedPropertyName == null || observedPropertyDescription == null || observedPropertyDefinition == null) {
            setErrorMessage("ObservedProperty is lacking name, description or definition field.");
            throw new IllegalArgumentException(getErrorMessage());
        }

        int count = 0;
        for (Object element : getResponse.getValue()) {
            JsonNode observedPropertyElementName = mapper.valueToTree(element).get("name");
            if (observedPropertyElementName.equals(observedPropertyName)) {
                if (count > 0) {
                    setErrorMessage("There are multiple observedProperties with the the name " + observedPropertyName + " registered in Gost server. Please clean this up.");
                    throw new IllegalStateException(getErrorMessage());
                }
                existingObservedProperty = mapper.valueToTree(element);
                count++;
            }
        }
        return existingObservedProperty;
    }

    private int postOrPatchObservedProperty(JsonNode observedProperty, JsonNode existingObservedProperty) {

        int observedPropertyId = 0;
        JsonNode observedPropertyName = observedProperty.get("name");
        JsonNode observedPropertyDescription = observedProperty.get("description");
        JsonNode observedPropertyDefinition = observedProperty.get("definition");

        if (existingObservedProperty != null/*observedPropertyId > 0*/) {

            JsonNode existingDescription = existingObservedProperty.get("description");
            JsonNode existingDefinition = existingObservedProperty.get("definition");
            int existingObservedPropertyId = existingObservedProperty.get("@iot.id").asInt();
            observedPropertyId = existingObservedPropertyId;

            if (!observedPropertyDescription.equals(existingDescription) || !observedPropertyDefinition.equals(existingDefinition)) {
                PatchObservedPropertyRequest patchObservedPropertyBody = new PatchObservedPropertyRequest();
                patchObservedPropertyBody.setDescription(observedPropertyDescription.asText());
                patchObservedPropertyBody.setDefinition(observedPropertyDefinition.asText());
                PatchObservedPropertyResponse patchResponse = null;
                controller.setHttpCallBack(httpResponse);
                try {
                    patchResponse = controller.patchObservedProperty(existingObservedPropertyId, contentType, patchObservedPropertyBody);
                    addObservedPropertiesReponse(mapper.valueToTree(patchResponse));
                } catch (APIException e) {
                    try {
                        JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                        addObservedPropertiesReponse(httpResponse);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        } else {
            PostObservedPropertyRequest postRequest = new PostObservedPropertyRequest();
            postRequest.setName(observedPropertyName.asText());
            postRequest.setDescription(observedPropertyDescription.asText());
            postRequest.setDefinition(observedPropertyDefinition.asText());
            PostObservedPropertyResponse postResponse = null;
            controller.setHttpCallBack(httpResponse);
            try {
                postResponse = controller.postObservedProperty(contentType, postRequest);
                addObservedPropertiesReponse(mapper.valueToTree(postResponse));
                observedPropertyId = postResponse.getMIotId();
            } catch (APIException e) {
                try {
                    JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                    addObservedPropertiesReponse(httpResponse);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        return observedPropertyId;
    }

    private JsonNode getExistingThing(JsonNode thing) {
        GetThingsResponse getResponse = null;
        try {
            getResponse = controller.getThings();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        JsonNode existingThing = null;
        JsonNode thingName = thing.get("name");
        JsonNode thingDescription = thing.get("description");

        if (thingName == null || thingDescription == null) {
            setErrorMessage("Thing is lacking name or description field.");
            throw new IllegalArgumentException(getErrorMessage());
        }

        int count = 0;
        for (Object element : getResponse.getValue()) {
            JsonNode thingElementName = mapper.valueToTree(element).get("name");
            if (thingElementName.equals(thingName)) {
                if (count > 0) {
                    setErrorMessage("There are multiple things with the the name " + thingName + " registered in Gost server. Please clean this up.");
                    throw new IllegalStateException(getErrorMessage());
                }
                existingThing = mapper.valueToTree(element);
                count++;
            }
        }
        return existingThing;
    }

    private int postOrPatchThing(JsonNode thingNode, JsonNode existingThing, List<Integer> locationIDs) {

        JsonNode thingName = thingNode.get("name");
        JsonNode thingDescription = thingNode.get("description");
        JsonNode thingProperties = thingNode.get("properties");
        ObjectNode thingPropertiesNode = mapper.createObjectNode();
        int thingId = 0;

        if (existingThing != null) {

            JsonNode existingDescription = existingThing.get("description");
            JsonNode existingProperties = existingThing.get("properties");
            int existingThingId = existingThing.get("@iot.id").asInt();
            thingId = existingThingId;

            boolean propertiesChanged = false;
            if ((thingProperties != null ^ existingProperties != null) || retryStatements) {
                propertiesChanged = true;
                retryStatements = false;
            } else {
                if (thingProperties != null) {
                    propertiesChanged = !thingProperties.equals(existingProperties);
                }
            }
            if (!thingDescription.equals(existingDescription) || propertiesChanged) {
                PatchThingRequest patchRequest = new PatchThingRequest();
                patchRequest.setDescription(thingNode.get("description").asText());
                if (thingProperties != null) {
                    Iterator<String> propertiesFields = thingProperties.fieldNames();
                    while (propertiesFields.hasNext()) {
                        String propertyName = propertiesFields.next();
                        JsonNode propertyValue = thingProperties.get(propertyName);
                        thingPropertiesNode.set(propertyName, propertyValue);
                    }
                    patchRequest.setProperties(thingProperties);
                }
                PatchThingResponse patchResponse = null;
                controller.setHttpCallBack(httpResponse);
                try {
                    patchResponse = controller.patchThing(existingThingId, contentType, patchRequest);
                    addThingsReponse(mapper.valueToTree(patchResponse));
                } catch (APIException e) {
                    try {
                        JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                        addThingsReponse(httpResponse);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        } else {
            PostThingWithExistingLocationRequest postRequest = new PostThingWithExistingLocationRequest();
            postRequest.setName(thingName.asText());
            postRequest.setDescription(thingNode.get("description").asText());
            if (thingProperties != null) {
                Iterator<String> propertiesFields = thingNode.get("properties").fieldNames();
                while (propertiesFields.hasNext()) {
                    String propertyName = propertiesFields.next();
                    JsonNode propertyValue = thingProperties.get(propertyName);
                    thingPropertiesNode.set(propertyName, propertyValue);
                }
                postRequest.setProperties(thingProperties);
            }
            List<Object> locationsList = new ArrayList<Object>();
            for (Integer locationID : locationIDs) {
                ObjectNode locationNode = mapper.createObjectNode();
                locationNode.put("@iot.id", locationID.toString());
                locationsList.add(locationNode);
            }
            postRequest.setLocations(locationsList);
            PostThingWithExistingLocationResponse postResponse = null;
            controller.setHttpCallBack(httpResponse);
            try {
                postResponse = controller.postThingWithExistingLocation(contentType, postRequest);
                addThingsReponse(mapper.valueToTree(postResponse));
                thingId = postResponse.getMIotId();
            } catch (APIException e) {
                try {
                    JsonNode httpResponse = mapper.readTree(e.getHttpContext().getResponse().getRawBody());
                    addThingsReponse(httpResponse);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        return thingId;
    }
}