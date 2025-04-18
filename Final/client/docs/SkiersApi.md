# SkiersApi

All URIs are relative to */*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getSkierDayVertical**](SkiersApi.md#getSkierDayVertical) | **GET** /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID} | get ski day vertical for a skier
[**getSkierResortTotals**](SkiersApi.md#getSkierResortTotals) | **GET** /skiers/{skierID}/vertical | get the total vertical for the skier for specified seasons at the specified resort
[**writeNewLiftRide**](SkiersApi.md#writeNewLiftRide) | **POST** /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID} | write a new lift ride for the skier

<a name="getSkierDayVertical"></a>
# **getSkierDayVertical**
> Integer getSkierDayVertical(resortID, seasonID, dayID, skierID)

get ski day vertical for a skier

get the total vertical for the skier for the specified ski day

### Example
```java
// Import classes:
//import io.swagger.client.ApiException;
//import io.swagger.client.api.SkiersApi;


SkiersApi apiInstance = new SkiersApi();
Integer resortID = 56; // Integer | ID of the resort the skier is at
String seasonID = "seasonID_example"; // String | ID of the ski season
String dayID = "dayID_example"; // String | ID number of ski day in the ski season
Integer skierID = 56; // Integer | ID of the skier riding the lift
try {
    Integer result = apiInstance.getSkierDayVertical(resortID, seasonID, dayID, skierID);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling SkiersApi#getSkierDayVertical");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **resortID** | **Integer**| ID of the resort the skier is at |
 **seasonID** | **String**| ID of the ski season |
 **dayID** | **String**| ID number of ski day in the ski season |
 **skierID** | **Integer**| ID of the skier riding the lift |

### Return type

**Integer**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="getSkierResortTotals"></a>
# **getSkierResortTotals**
> SkierVertical getSkierResortTotals(skierID, resort, season)

get the total vertical for the skier for specified seasons at the specified resort

get the total vertical for the skier the specified resort. If no season is specified, return all seasons

### Example
```java
// Import classes:
//import io.swagger.client.ApiException;
//import io.swagger.client.api.SkiersApi;


SkiersApi apiInstance = new SkiersApi();
Integer skierID = 56; // Integer | ID the skier to retrieve data for
List<String> resort = Arrays.asList("resort_example"); // List<String> | resort to filter by
List<String> season = Arrays.asList("season_example"); // List<String> | season to filter by, optional
try {
    SkierVertical result = apiInstance.getSkierResortTotals(skierID, resort, season);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling SkiersApi#getSkierResortTotals");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **skierID** | **Integer**| ID the skier to retrieve data for |
 **resort** | [**List&lt;String&gt;**](String.md)| resort to filter by |
 **season** | [**List&lt;String&gt;**](String.md)| season to filter by, optional | [optional]

### Return type

[**SkierVertical**](SkierVertical.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="writeNewLiftRide"></a>
# **writeNewLiftRide**
> writeNewLiftRide(body, resortID, seasonID, dayID, skierID)

write a new lift ride for the skier

Stores new lift ride details in the data store

### Example
```java
// Import classes:
//import io.swagger.client.ApiException;
//import io.swagger.client.api.SkiersApi;


SkiersApi apiInstance = new SkiersApi();
LiftRide body = new LiftRide(); // LiftRide | Specify new Season value
Integer resortID = 56; // Integer | ID of the resort the skier is at
String seasonID = "seasonID_example"; // String | ID of the ski season
String dayID = "dayID_example"; // String | ID number of ski day in the ski season
Integer skierID = 56; // Integer | ID of the skier riding the lift
try {
    apiInstance.writeNewLiftRide(body, resortID, seasonID, dayID, skierID);
} catch (ApiException e) {
    System.err.println("Exception when calling SkiersApi#writeNewLiftRide");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **body** | [**LiftRide**](LiftRide.md)| Specify new Season value |
 **resortID** | **Integer**| ID of the resort the skier is at |
 **seasonID** | **String**| ID of the ski season |
 **dayID** | **String**| ID number of ski day in the ski season |
 **skierID** | **Integer**| ID of the skier riding the lift |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

