package example;

import java.io.IOException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

//import 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.StringBuilder;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.FileNotFoundException;
//import java.io.IOException;
import java.io.InputStream;

import example.V2MessageConverter;
import example.pojo.patient.Patient;
import example.pojo.observation.Observation;


//TODO implementation for s3 event
public class HandlerS3 implements RequestHandler<S3Event, String> {
  private static String ENV_API_END_POINT = "ENV_API_END_POINT";
  
  Gson gson = new GsonBuilder().setPrettyPrinting().create();
  
  //TODO put it into API Gateway (rest)
  CognitoAuth auth = new CognitoAuth();
  
  private static final Logger logger = LoggerFactory.getLogger(HandlerS3.class);
  @Override
  public String handleRequest(S3Event s3event, Context context) {
    logger.info("EVENT: " + gson.toJson(s3event));
    S3EventNotificationRecord record = s3event.getRecords().get(0);
    
    String srcBucket = record.getS3().getBucket().getName();
    logger.info("Source bucket: " + srcBucket);
    // Object key may have spaces or unicode non-ASCII characters.
    String srcKey = record.getS3().getObject().getUrlDecodedKey();
    logger.info("Source key: " + srcKey);
    
    // get token
    String token = auth.sightIn();
    
    //TODO get object and transform to new format

    Patient pat = null;
    Observation[] obxs = null;
    try{
      AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.DEFAULT_REGION).build();
      S3Object o = s3.getObject(srcBucket, srcKey);
      S3ObjectInputStream s3is = o.getObjectContent();
      V2MessageConverter conv = new V2MessageConverter((InputStream)s3is);

      pat = conv.getPatient();
      obxs = conv.getObservations();
    // } catch (AmazonServiceException e) {
    //   System.err.println(e.getErrorMessage());
    //   System.exit(1);
    // } catch (FileNotFoundException e) {
    //   System.err.println(e.getMessage());
    //   System.exit(1);
    // } catch (IOException e) {
    //   System.err.println(e.getMessage());
    //   System.exit(1);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
    
    //TODO put it into API Gateway (rest)
    String fhirPatient = gson.toJson(pat);
    String[] fhirObservations = new String[obxs.length];
    for(int i = 0; i < obxs.length; i++){
      fhirObservations[i] = gson.toJson(obxs[i]);
    }    

    //TODO put it into API Gateway (rest)
    ApiGatewayClient client = new ApiGatewayClient();
    String baseurl = System.getenv(ENV_API_END_POINT);
    String path = "/Patient";
    try{
      client.post(baseurl, path, token, fhirPatient);
    }catch (Exception e) {
            e.printStackTrace();
    }

    //TODO put it into s3 destination

    return null;
  }
}

// TODO need to add test for HandlerS3