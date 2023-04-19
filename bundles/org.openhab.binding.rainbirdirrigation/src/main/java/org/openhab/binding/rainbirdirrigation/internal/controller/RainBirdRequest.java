package org.openhab.binding.rainbirdirrigation.internal.controller;

public class RainBirdRequest {
    private int id = 9;
    private String jsonrpc = "2.0";
    private String method = "tunnelSip";
    private Params params;

    public RainBirdRequest(Params params) {
        this.params = params;
    }

    static class Params {
        private String command;
        private int length;

        public Params(String command, int length) {
            this.command = command;
            this.length = length;
        }
    }
}
