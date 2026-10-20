package traincraft.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.fml.loading.FMLPaths;

/** DEV54F: Exact SHA-guarded legacy TCM rod-only visibility; never edits data or wheel motion. */
public final class Dev54FRodMask {
    private static final Map<String,String> EXPECTED = new HashMap<>();
    private static final Map<String,BitSet> RODS = new HashMap<>();
    private static volatile long nextCheckNanos = 0;
    private static volatile boolean cachedOn = false;
    private Dev54FRodMask() {}
    private static void add(String name,String hash,int[] chunks) {
        EXPECTED.put(name,hash.toLowerCase(java.util.Locale.ROOT));
        BitSet bits = new BitSet();for(int c:chunks)bits.set(c);RODS.put(name,bits);
    }
    static {
        add("locomotive_steam_262t", "9dc0f4d11d58cd187be62890c7e5b6565a157152d363d3441e4c3d307c60fb47", new int[]{29,30,31,32});
        add("locomotive_steam_4_4_0", "2ed8c998e1659a31d78ef855046161fb95613d5a5b370781ef783673ab063a2d", new int[]{64,65,66,67,81});
        add("locomotive_steam_a4_mallard", "172ba1d0f7180892d39733ab15cf539d7aff1c586b6abfbeda52c1970ff84175", new int[]{87,88,91,301,304,305,306,307,312,313,317,334,335,338,342,351,354,355,363,366});
        add("locomotive_steam_adler", "3b3abbb3f2d9ad777977ab703d504166d521533a00b06cac5dd8af774ceccc32", new int[]{68,71,145,146,252,253});
        add("locomotive_steam_alco_sc4", "ea6ef46a62df7b89cfe8b1ac31c872ed14eeb12253eb91cb99ff3d59d1bad24e", new int[]{31,32,33,34,35,37,38,39,40,45,46,47,342,343,344,345,349,350,352,353,354,548,549,550,552,553,554,558,559,561,562,565,566,567,568,570,571,572,573,614,615,616,621,622});
        add("locomotive_steam_alice_0_4_0", "db87b7f1c6177269203b8d2025c7cbbd34ff6c0f441a1674731f11ecad263771", new int[]{131,132,134,136});
        add("locomotive_steam_berkshire_1225", "9ad06147c0290ff22c33a568ddf664cca3bab00704824bb390dc960e4b818865", new int[]{348,372,378,490,491,734,747});
        add("locomotive_steam_berkshire_765", "8a5588375bc8d284b29dcba73984bf4ea127232209de03fdb6aa744b7828a6c4", new int[]{348,372,378,490,491,729,733,746,1013,1022});
        add("locomotive_steam_br80", "5396f886ea7ae2b05afb6254f0320906ec0d660b12b025206f1e45764a0000a9", new int[]{50,51,52,53,54,55});
        add("locomotive_steam_br_black_5", "88b98ec7c86698dee9614886515f04aa1fa9373eb83118df4231fba8c98aa328", new int[]{204,205,206,211,215,218,219,220,222,223,224,226,237,238,239,244,248,251,252,253,255,257,259});
        add("locomotive_steam_br_britannia", "163a759bde575b7d4c6c170353905c8cb8f83788cfc56435247420b3494a0ed0", new int[]{206,207,211,216,217,233,236,237,240,241,246,247,249,250,251,341,344,345,346,350,351,353,354,355,363});
        add("locomotive_steam_c11", "62d540ad3ad66d4ec7056a125660bdcc20bb3debbc8081d640c5e4a7b90b4959", new int[]{82,86,87,276,279,283,290,292,300,308,313,317,319,321,322,326,331,334,342,387,388});
        add("locomotive_steam_c41", "37188413d060738ebdabb9abf802b9643820d12ef399194e3fb0a94ba141b0c0", new int[]{45,46,47,48,49,50,53,422,423,424,425,426,427,433,435,436});
        add("locomotive_steam_c41_080", "93ccae40ddadb884073d88a6cf79b53f6d2617b98d5a8737505af9f281d06941", new int[]{35,36,37,38,39,40,41,42,43,44,45,46,50,386,387,389,394,395,397,398,399,400,401,402,408});
        add("locomotive_steam_c41t", "ae488515e44dda3fb2bdf629e0252feecb14d563c50a7898370993a9c0d5ace1", new int[]{41,42,43,44,46,381,382,383,384,385,392,394});
        add("locomotive_steam_climax", "121896af8c0e820e9bbf1f78a0cd22307604a7e3a1050d96fe92dc140d4b59ed", new int[]{22,47,59,178,184});
        add("locomotive_steam_climax_2", "3298f9a26416aba3277beae6e6848d14513f603ea6b6b5b5eef4b1b25b9c5a3f", new int[]{72,78,87,92,96,97,98,100,103});
        add("locomotive_steam_coronation_class", "3108d41aa92abce5e0d6319afd593c42813fb00d2f7fa352782256ccd7bb3ad8", new int[]{29,35,36,51,53,65,66,81,82,83,84,85,86,88,89,90,379,380,381,382,383,384});
        add("locomotive_steam_fowler", "cedceff96571a828e4b9c69d375ad69f63d105332bf48f7f4b3b11ee875f9a28", new int[]{82,83});
        add("locomotive_steam_gs4", "4b0ccc2822bef70fc3664f07c7091ff6193741bc59f57b7f6dbb892fe93006c3", new int[]{73,75,77,78,79,80,81,82,83,99,100,102,103,104,106,107,108,109});
        add("locomotive_steam_gwr_101_class", "6c021e73387d5ae79a885f9ca5ddd5654fafcfa16dae52fc2b64f527059b5795", new int[]{59,61,62,63,64,72,73,74,75,76,93,146});
        add("locomotive_steam_gwr_42xx", "eefe1fae505dd708cfa8b47e391f831625ebeba44858ff45055efe215cde90d4", new int[]{9,10,11,12,13,14,15,16,17,18,22,278});
        add("locomotive_steam_gwr_72xx", "6ef457f7b46cc0018b561904e3c984852712050c67ad05618eb34e9803c3ccc4", new int[]{9,10,11,12,13,14,15,16,17,18,24,280});
        add("locomotive_steam_hall_class", "713ad8bff748e52573eb25b02a320f66766d4f1358acd60b41a01fb6c9b48e06", new int[]{442,443,444,445,446,448,451,455,461,462,463,468,473,474,492,493,502,503,504,529,530,531,547,548,549,556,557,558,562,563,564,579,580,581,593,594,595,897,898,899});
        add("locomotive_steam_j50", "c0a8a051a1dccc9982b7b292773cda8ce1357076498aa6741302fe706da574ce", new int[]{25,27,28,29,31,32,37,39,40,41,43,44,616,617,618,619});
        add("locomotive_steam_king_class", "69da8e5507d314e6ddeaea25c58db157ad7c8ce8e3f68d256039c127b03324b9", new int[]{478,479,480,482,484,485,488,491,494,495,498,499,500,517,518,519,533,534,535,544,545,546,560,561,562,567,590,591,593,594,595,596,597,599,600,601,602,603,605,606,607,608,609,610,611,614,615,617,640,641,642,662,663,667,669,688});
        add("locomotive_steam_lssp7", "90d29ebf1278bb7eb3cc21d20c631a89056ed4a3503dd51bc3365acfdf48a401", new int[]{43,44});
        add("locomotive_steam_midland_compound", "56a825a205c77326e52e5cf08755fcd9417244955d3281e2a54a75ede2d20dbc", new int[]{38,39,40,43,44,45,82,83,84,85,87,88,93,94,117,118,119,121,124,125});
        add("locomotive_steam_milw_class_a", "cba9dd89c1d287367e9762525a4c16393c10c81f9d95f304c807038539b32961", new int[]{103,104,129,157,169,173,174,175,177,178,181,187,188,189,190,191,196,198});
        add("locomotive_steam_pannier", "cb473f135e1b1bec08ec620222382ec31b65420e4f01599d5e1dbc7280997ebc", new int[]{1,329,333,334});
        add("locomotive_steam_pe", "cc3d8bf40a34e695b06cc617eeeee39fb1b76aaa510cefaf16bed62b30e3b4b5", new int[]{206,207,211,212,214,215,217,219,220,222,228,232,233,241,242,243,245,246,249});
        add("locomotive_steam_rw_type_2", "8201e113277cc21adc203976c04c792dbd090a19c67c5012df57dc8f9bfe85b6", new int[]{63,65,66,67,68,69,70,72,73,74,75,77,79,80,81,83,84,85,86,87,88,89,90,91,92,93});
        add("locomotive_steam_rw_type_3", "e1ac625f31296caa1ebfc7aabde6001a18f437411795ef6cb8bc29fdc8f58a96", new int[]{115,119,122,133,137,140,146});
        add("locomotive_steam_s100_uk", "0361cee0f5bba4da0ea541cacd9ee0c4615cf88ecdae604ad9ea003687352fae", new int[]{398,399,400,401,402,403,404,408,409,411,412,413,414,415,416,417,418,419,423,424,425,427,428,429,430,431,433,434,435,437,439,442,443,445,446,447,449,450,451,452,454,455,456,457});
        add("locomotive_steam_s100_us", "06bfad8cda5de0472dd77b532353bc9d98fec40dfa62637f6f56bc8d311b88c7", new int[]{323,324,325,326,327,328,329,333,334,340,341,342,343,344,345,346,347,348,405,406,407,408,409,410,411,412,413,435,436,437,439,441,445,447,448,452,453,454,456,457,459});
        add("locomotive_steam_sentinel_y3", "f2557379bd6465fb0c7a9ed71edfb8def45a8ccd7fdc3f4106d98c8c0680ceca", new int[]{72,96,97,98});
        add("locomotive_steam_shay_3_truck", "37b23dec217825d10f5c8e1b368503ad858f8fe7b1a4bc8b10a6d09612d3f71e", new int[]{179,180,186,187,189,190,195,201,413,516});
        add("locomotive_steam_skookum", "999cecaaa803e4f8cf147544b148a2405c2457b9147294051df0859314445f0d", new int[]{121,122,123,124,126,127,128,129,131,132,133,134,136,137,138,139,140,142,143,144});
        add("locomotive_steam_southern1102", "ccd566fbe11b01c2f5784653a0cf405b9a7a7336f297c994c8302fc6a7259dfe", new int[]{36,37,38,44,45,275,276,277,278,282,283,285,286,287,288});
        add("locomotive_steam_u57", "9553fe1e20c6a2965a8251efcd75a771e897ae1708be1922a00085ab5b2f091a", new int[]{3,6,8,35,58,59});
        add("locomotive_steam_vb_0_4_0", "8094f590a8d49664a51272e90d0d8664cf37881c82383de31f6d62c2a8aaad40", new int[]{9,10});
        add("locomotive_steam_vb_shay", "1fc1209bc0288fb61671645ff3d7c2693e1c1a42115e63cf3c6db0e8da93a179", new int[]{20,24,32,33,46,68});
        add("locomotive_steam_vb_shay_2", "e68f978ca29fa3aa46ecbb6e65bf0367a17602a9846881a5ae8635d706656b50", new int[]{48,49,50,51,53});
        add("locomotive_steam_wwcp_062t", "04ce9253aed0850af62e5a4895ee73abb70ee066229b71ba251612db5d1773cf", new int[]{24,26,85,96,97,98,132,133,154,156});
    }
    public static boolean matches(String name,byte[] bytes) {
        String expected=EXPECTED.get(name);
        if(expected==null)return false;
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder b=new StringBuilder(64);
            for(byte x:hash)b.append(String.format("%02x",x&255));
            return expected.equals(b.toString());
        }catch(Exception ex){return false;}
    }
    public static boolean isRodOnly(String sourcePath,int chunk) {
        String name=sourcePath.replace("legacy_meshes/", "").replace(".tcm", "");
        BitSet b=RODS.get(name);return b!=null && b.get(chunk);
    }
    public static boolean enabled() {
        long now=System.nanoTime();if(now<nextCheckNanos)return cachedOn;
        // Only a DEV54F active session renews the 12-second deadline; a crash expires harmlessly.
        boolean on=false;
        try {
            Path f=FMLPaths.GAMEDIR.get().resolve("DEV54F-RODS-HIDDEN-ON.txt");
            if(Files.isRegularFile(f)) {
                String s=Files.readString(f,StandardCharsets.UTF_8).strip();
                if(s.startsWith("ON ")) {
                    long expiry=Long.parseLong(s.substring(3).strip());
                    on=expiry>=System.currentTimeMillis() && expiry<System.currentTimeMillis()+30000L;
                }
            }
        }catch(Exception ignored) {on=false;}
        cachedOn=on;nextCheckNanos=now+500_000_000L;return on;
    }
}
