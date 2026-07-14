package com.moamap.place.controller;

import java.util.List;
import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.dto.PlaceResponse;
import com.moamap.place.dto.PlaceUpdateRequest;
import com.moamap.place.service.PlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlaceResponse> create(
        @Valid @RequestBody PlaceCreateRequest request,
        @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.success(placeService.create(request, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlaceResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(placeService.findById(id));
    }

    @GetMapping
    public ApiResponse<List<PlaceResponse>> getAll(@RequestParam Long mapId) {
        return ApiResponse.success(placeService.findAllByMapId(mapId));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PlaceResponse> update(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody PlaceUpdateRequest request
    ) {
        return ApiResponse.success(placeService.update(id, userId, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId
    ) {
        placeService.delete(id, userId);
    }
}
