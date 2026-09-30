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
        NOBODY,
        FRIENDS,
        EVERYONE;

        public static Filter parse(String raw) {
            if (raw == null) {
                return EVERYONE;
            }
            return switch (raw.trim().toUpperCase()) {
                case "FRIENDS", "FRIEND" -> FRIENDS;
                case "NOBODY", "NONE", "NOONE", "NO_ONE", "OFF" -> NOBODY;
                default -> EVERYONE;
            };
        }

        public Filter next() {
            return switch (this) {
                case NOBODY -> FRIENDS;
                case FRIENDS -> EVERYONE;
                case EVERYONE -> NOBODY;
            };
        }

        public String pretty() {
            return switch (this) {
                case NOBODY -> "Noone";
                case FRIENDS -> "Friends";
                case EVERYONE -> "Everyone";
            };
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

        public InstantAllow next() {
            return switch (this) {
                case NOBODY -> FRIENDS;
                case FRIENDS -> ANYONE;
                case ANYONE -> NOBODY;
            };
        }

        public String pretty() {
            return switch (this) {
                case NOBODY -> "Noone";
                case FRIENDS -> "Friends";
                case ANYONE -> "Everyone";
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
