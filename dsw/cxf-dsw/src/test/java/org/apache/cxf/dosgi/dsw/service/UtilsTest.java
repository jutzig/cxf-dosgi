/** 
  * Licensed to the Apache Software Foundation (ASF) under one 
  * or more contributor license agreements. See the NOTICE file 
  * distributed with this work for additional information 
  * regarding copyright ownership. The ASF licenses this file 
  * to you under the Apache License, Version 2.0 (the 
  * "License"); you may not use this file except in compliance 
  * with the License. You may obtain a copy of the License at 
  * 
  * http://www.apache.org/licenses/LICENSE-2.0 
  * 
  * Unless required by applicable law or agreed to in writing, 
  * software distributed under the License is distributed on an 
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
  * KIND, either express or implied. See the License for the 
  * specific language governing permissions and limitations 
  * under the License. 
  */
package org.apache.cxf.dosgi.dsw.service;


import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sun.corba.se.impl.javax.rmi.CORBA.Util;

import org.junit.Test;


public class UtilsTest {

    @Test
    public void testNormalizeStringPlus(){
        
        String s1 = "s1";
        String s2 = "s2";
        String s3 = "s3";
        
        String[] sa = new String[] {s1,s2,s3};
        
        Collection<Object> sl = new ArrayList<Object>(4);
        sl.add(s1);
        sl.add(s2);
        sl.add(s3);
        sl.add(new Object()); // must be skipped
        
        assertEquals(null,Utils.normalizeStringPlus(new Object()));
        assertEquals(new String[] {s1},Utils.normalizeStringPlus(s1));
        assertEquals(sa,Utils.normalizeStringPlus(sa));
        assertEquals(sa,Utils.normalizeStringPlus(sl));
        
    }
    
    
}