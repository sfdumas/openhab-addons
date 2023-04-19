package org.openhab.binding.rainbirdirrigation.internal.controller;

public enum RainBirdResponse {

    MODEL_AND_VERSION(5);

    private final int length;

    RainBirdResponse(int length) {
        this.length = length;
    }

    public int getLength() {
        return length;
    }
}
