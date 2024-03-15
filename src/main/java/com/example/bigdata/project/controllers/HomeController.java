package com.example.bigdata.project.controllers;


import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;



import jakarta.servlet.http.HttpServletRequest;
@Controller
@RequestMapping("/v1")
public class HomeController {


    @GetMapping("")
    public ResponseEntity<Object> healthCheck(HttpServletRequest request){

        

        return new ResponseEntity<>(null);
        

        
    }

    
    
    
}

