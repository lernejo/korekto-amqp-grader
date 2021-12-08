package com.github.lernejo.korekto.grader.amqp;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;

public interface ChatApiClient {

    @GET("api/message")
    //@Headers("Accept:application/json")
    Call<List<String>> getMessages();
}
