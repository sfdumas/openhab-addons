package org.openhab.binding.rainbirdirrigation.internal.controller;

public enum RainBirdCommand {

    MODEL_AND_VERSION("02", 1, RainBirdResponse.MODEL_AND_VERSION);

    private final String command;
    private final int length;
    private final RainBirdRequest request;
    private final RainBirdResponse response;

    RainBirdCommand(String command, int length, RainBirdResponse response) {
        this.command = command;
        this.length = length;
        this.request = new RainBirdRequest(new RainBirdRequest.Params(command, length));
        this.response = response;
    }

    public String getCommand() {
        return this.command;
    }

    public int getLength() {
        return length;
    }

    public RainBirdRequest getRequest() {
        return request;
    }

    public RainBirdResponse getResponse() {
        return response;
    }
}
