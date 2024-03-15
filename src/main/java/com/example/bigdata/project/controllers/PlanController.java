package com.example.bigdata.project.controllers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;

import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.bigdata.project.service.AuthService;
import com.example.bigdata.project.service.PlanService;
import com.example.bigdata.project.validators.JSONValidator;


@RestController
public class PlanController {

    @Autowired
    JSONValidator jsonValidator;

    @Autowired
    PlanService planService;

    @Autowired
    AuthService auth;

    Map<String, Object> map = new HashMap<String, Object>();
    


    

    @PostMapping(path = "/plan/", produces = "application/json")
    public ResponseEntity<Object> createPlan(@RequestBody(required = false) String medicalPlan, @RequestHeader HttpHeaders headers) throws JSONException, Exception {
        
        String authToken = (headers.getFirst("Authorization") != null) ? headers.getFirst("Authorization").split(" ")[1] : "";
        System.out.println("Token:"+authToken);
        if(!auth.verify(authToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", authToken).toString());
        }
        map.clear();

        if(medicalPlan == null || medicalPlan.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", " The request body is empty. Please provide a valid JSON payload in the request body.").toString());
        }

        JSONObject json = new JSONObject(medicalPlan);
        try {
            jsonValidator.validateJson(json);
        } catch(ValidationException ve){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error",ve.getErrorMessage()).toString());

        }

        String key = json.get("objectType").toString() + ":" + json.get("objectId").toString() + ":";
        if(planService.checkIfKeyExists(key)){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("Message", "Plan already exist.").toString());
        }

        String newEtag = planService.savePlanToRedis(json, key);

        return ResponseEntity.ok().eTag(newEtag).body(" {\"message\": \"Created data with key: " + json.get("objectId") + "\" }");
    }

    @GetMapping(path = "/{type}/{objectId}", produces = "application/json")
    public ResponseEntity<Object> getPlan(@RequestHeader HttpHeaders headers, @PathVariable String objectId,@PathVariable String type) throws JSONException, Exception {

        String authToken = (headers.getFirst("Authorization") != null) ? headers.getFirst("Authorization").split(" ")[1] : "";
        if(!auth.verify(authToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", authToken).toString());
        }
        
        if (!planService.checkIfKeyExists(type + ":" + objectId + ":")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        String actualEtag = null;
        if (type.equals("plan")) {
            actualEtag = planService.getEtag(type + ":" + objectId + ":", "eTag");
            String eTag = headers.getFirst("If-None-Match");
            if (eTag != null && eTag.equals(actualEtag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(actualEtag).body(new JSONObject().put("Message", "Not Modified").toString());
            }
        }

        String key = type + ":" + objectId + ":";
        Map<String, Object> plan = planService.getPlan(key);

        if (type.equals("plan")) {
            return ResponseEntity.ok().eTag(actualEtag).body(new JSONObject(plan).toString());
        }

        return ResponseEntity.ok().body(new JSONObject(plan).toString());
    }

    @DeleteMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> getPlan(@RequestHeader HttpHeaders headers, @PathVariable String objectId){

        String authToken = (headers.getFirst("Authorization") != null) ? headers.getFirst("Authorization").split(" ")[1] : "";
        if(!auth.verify(authToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", authToken).toString());
        }
        
        if (!planService.checkIfKeyExists("plan"+ ":" + objectId + ":")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }
        String key = "plan:" + objectId + ":";
        String actualEtag = planService.getEtag(key, "eTag");
        String eTag = headers.getFirst("If-Match");
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).body(new JSONObject().put("Message", "Precondition Failed").toString());
        }
        if(eTag == null) return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).body(new JSONObject().put("Message", "Precondition Failed").toString());

        Map<String, Object> plan = planService.getPlan(key);
        
        planService.deletePlan("plan" + ":" + objectId + ":");

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(new JSONObject().put("Message", "Deleted Data").toString());


    }

    @PutMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> updatePlan(@RequestHeader HttpHeaders headers, @Valid @RequestBody String medicalPlan,
                                             @PathVariable String objectId) throws IOException {

        String authToken = (headers.getFirst("Authorization") != null) ? headers.getFirst("Authorization").split(" ")[1] : "";
        if(!auth.verify(authToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", authToken).toString());
        }

        JSONObject planObject = new JSONObject(medicalPlan);
        try {
            jsonValidator.validateJson(planObject);
        } catch (ValidationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("Validation Error", ex.getMessage()).toString());
        }

        String key = "plan:" + objectId + ":";
        if (!planService.checkIfKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        // Get eTag value
        String actualEtag = planService.getEtag(key, "eTag");
        String eTag = headers.getFirst("If-Match");
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).body(new JSONObject().put("Message", "Precondition Failed").toString());
        }

        Map<String, Object> oldPlan = planService.getPlan(key);

       
        planService.deletePlan(key);

        String newEtag = planService.savePlanToRedis(planObject, key);

        return ResponseEntity.ok().eTag(newEtag).body(" {\"message\": \"Created data with key: " + planObject.get("objectId") + "\" }");

        // return ResponseEntity.ok().eTag(newEtag)
        //         .body(new JSONObject().put("Message: ", "Resource updated successfully").toString());
    }

    @PatchMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> patchPlan(@RequestHeader HttpHeaders headers, @Valid @RequestBody String medicalPlan,
                                            @PathVariable String objectId) throws IOException {


        String authToken = (headers.getFirst("Authorization") != null) ? headers.getFirst("Authorization").split(" ")[1] : "";
        if(!auth.verify(authToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", authToken).toString());
        }
        JSONObject planObject = new JSONObject(medicalPlan);

        String key = "plan:" + objectId + ":";
        if (!planService.checkIfKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        try {
            jsonValidator.validateJson(planObject);
        } catch(ValidationException ve){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error",ve.getErrorMessage()).toString());

        }

        String actualEtag = planService.getEtag(key, "eTag");
        String eTag = headers.getFirst("If-Match");
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).body(new JSONObject().put("Message", "Precondition Failed").toString());
        }
        if(eTag == null) return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).body(new JSONObject().put("Message", "Precondition Failed").toString());

        String newEtag = planService.savePlanToRedis(planObject, key);

        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put("Message: ", "Resource updated successfully").toString());
    }
}
