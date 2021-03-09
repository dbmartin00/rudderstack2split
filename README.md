# Rudderstack to Split Events Integration

Use a Rudderstack events webhook to extract, transform, and load events to Split.
This first version of the integration has not been tested for performance; Rudderstack passes events one at a time.

![alt text](http://www.cortazar-split.com/rudderstack2split.png)

1. Deploy as a Google Cloud Function

Jar the source code for Google Cloud Function

```
jar -cvf r2s.jar pom.xml src\/*
```

Using the Google Cloud Console, create a new webhook and upload the JAR file to deploy.  Copy and store the webhook URL.

2. Register webhook as a Destination in Rudderstack

In Rudderstack, create a new Destination webhook.  Make sure it is connected to an event source.

Use these custom headers:

```
splitApiKey : Split server-side SDK key
trafficType : e.g. user or anonymous
environmentName : e.g Prod-Default
```

Provide the webhook URL you created in step one.

3. Monitor incoming event traffic in Split Data Hub

Look for new Rudderstack events in the Split Data Hub event's stream.

For troubleshooting, check out the GCF log.

David.Martin@split.io
3-9-2021


