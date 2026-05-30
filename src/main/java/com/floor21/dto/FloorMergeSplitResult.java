package com.floor21.dto;

import com.floor21.entity.Flat;

public record FloorMergeSplitResult(Flat keep, Flat restored) {}
