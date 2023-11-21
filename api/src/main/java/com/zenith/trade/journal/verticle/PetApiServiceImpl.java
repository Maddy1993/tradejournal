package com.zenith.trade.journal.verticle;

import com.zenith.trade.journal.verticle.model.Pet;

import com.zenith.trade.journal.ApiResponse;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;

// Implement this class

public class PetApiServiceImpl implements PetApi {
    public Future<ApiResponse<Void>> addPet(Pet pet) {
        return Future.failedFuture(new HttpException(501));
    }

    public Future<ApiResponse<Pet>> getPetById(Long petId) {
        final Pet returnVal = new Pet();
        returnVal.setName("Test Pet");
        return Future.succeededFuture(new ApiResponse<>(returnVal));
    }
}
