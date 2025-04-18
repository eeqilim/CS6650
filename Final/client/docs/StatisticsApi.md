# StatisticsApi

All URIs are relative to */*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getPerformanceStats**](StatisticsApi.md#getPerformanceStats) | **GET** /statistics | get the API performance stats

<a name="getPerformanceStats"></a>
# **getPerformanceStats**
> APIStats getPerformanceStats()

get the API performance stats

### Example
```java
// Import classes:
//import io.swagger.client.ApiException;
//import io.swagger.client.api.StatisticsApi;


StatisticsApi apiInstance = new StatisticsApi();
try {
    APIStats result = apiInstance.getPerformanceStats();
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling StatisticsApi#getPerformanceStats");
    e.printStackTrace();
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**APIStats**](APIStats.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

