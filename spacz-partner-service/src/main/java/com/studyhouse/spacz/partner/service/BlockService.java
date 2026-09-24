package com.studyhouse.spacz.partner.service;

import java.util.List;

import com.studyhouse.spacz.partner.dto.request.BlockRequest;
import com.studyhouse.spacz.partner.dto.response.BlockResponse;

public interface BlockService {

    BlockResponse createBlock(BlockRequest request);

    List<BlockResponse> getAllBlocks();

    BlockResponse getBlockById(Long id);

    List<BlockResponse> getBlocksByPropertyId(Long propertyId);

    BlockResponse updateBlock(Long id, BlockRequest request);

    void deleteBlock(Long id);
}
