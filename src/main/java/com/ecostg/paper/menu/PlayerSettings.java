package com.ecostg.paper.menu;

public record PlayerSettings(
        Filter chatFilter,
        Filter notifFilter,
        boolean nightVision,
        boolean moneyNametag,
        boolean showMoney,
        boolean showKills,
        boolean showDeaths,
        boolean showPlaytime,
        boolean showJob,
        InstantAllow instantTpa,
        InstantAllow instantTpaHere,
        boolean auctionEnabled,
        boolean jobsEnabled
) {
    public enum Filter {
        FRIENDS,
        EVERYONE;

        public static Filter parse(String raw) {
            if (raw == null) {
                return EVERYONE;
            }
            String v = raw.trim().toUpperCase();
            if (v.equals("FRIENDS") || v.equals("FRIEND")) {
                return FRIENDS;
            }
            return EVERYONE;
        }
    }

    public enum InstantAllow {
        ANYONE,
        FRIENDS,
        NOBODY;

        public static InstantAllow parse(String raw) {
            if (raw == null) {
                return NOBODY;
            }
            return switch (raw.trim().toUpperCase()) {
                case "ANYONE", "EVERYONE", "ALL" -> ANYONE;
                case "FRIENDS", "FRIEND" -> FRIENDS;
                default -> NOBODY;
            };
        }
    }

    public static PlayerSettings defaults() {
        return new PlayerSettings(
                Filter.EVERYONE,
                Filter.EVERYONE,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                InstantAllow.NOBODY,
                InstantAllow.NOBODY,
                true,
                true
        );
    }
}
