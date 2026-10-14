/*
 * Traincraft 1.20.1 legacy static-mesh bridge.
 * Step 8.4.0 Batch B1. Converts recovered CE 7.1 TMT geometry into a compact
 * resource while preserving box/shape-box UVs and stabilizing zero-thickness faces.
 */
package traincraft.client.model.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3f;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import java.nio.file.StandardOpenOption;

public final class LegacyStaticMesh {
    // DEV44 final fleet candidate: optional, model-SHA guarded, legacy STEAM ONLY.
    // Accepted Berkshire 765 remains entirely outside this pass.
    private int[] dev44FleetWheelChunks;
    private boolean dev44FleetFirstReport;
    private static boolean dev44FleetEnabled() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV44-FLEET-LINKAGE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                          .replace("\uFEFF","").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    // DEV44 R10 exact-label hide proof, never touches other Java-model locomotives.
    // Enable only after backing up and creating run/DEV44-R10-HIDE-ON.txt with word ON.
    // DEV44 R12: Disabled by default; reads opt-in marker dynamically without rebuild.
    // R12a: resolve BOTH project-root and Forge gameDir paths. Reject ambiguous/stale markers.
    // The supplied enable/disable CMDs write to projectRoot/run/.
    // R12b: use the actual Forge game directory, never an ambiguous cwd candidate.
    private static java.nio.file.Path dev44R12MarkerPath() {
        return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV44-R12-HIDE-ON.txt").toAbsolutePath().normalize();
    }
    private static boolean dev44R12HideEnabled() {
        try {
            java.nio.file.Path marker = dev44R12MarkerPath();
            return java.nio.file.Files.isRegularFile(marker)
                    && java.nio.file.Files.readString(marker, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) {
            LOGGER.warn("[TC-DEV44-R12b] Cannot read hide marker at {}", dev44R12MarkerPath(), ex);
            return false;
        }
    }
    private static boolean dev44R10ReadOptIn() {
        try {
            java.nio.file.Path p = java.nio.file.Path.of("DEV44-R10-HIDE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private static java.util.BitSet dev44R10Bits(int[] chunks) {
        java.util.BitSet b = new java.util.BitSet();
        for (int id : chunks) b.set(id);
        return b;
    }
    private static final java.util.BitSet DEV44_R10_IDS_0 = dev44R10Bits(new int[]{17, 19, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});
    private static final java.util.BitSet DEV44_R10_IDS_1 = dev44R10Bits(new int[]{8, 62, 63, 64, 65, 66, 67, 81});
    private static final java.util.BitSet DEV44_R10_IDS_2 = dev44R10Bits(new int[]{35, 36, 37, 38, 39, 40, 53, 54, 55, 56, 57, 59, 84, 85, 86, 87, 88, 89, 90, 91, 294, 295, 296, 297, 298, 299, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366});
    private static final java.util.BitSet DEV44_R10_IDS_3 = dev44R10Bits(new int[]{68, 71, 145, 146, 252, 253});
    private static final java.util.BitSet DEV44_R10_IDS_4 = dev44R10Bits(new int[]{31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 45, 46, 47, 273, 274, 275, 276, 277, 278, 279, 280, 281, 283, 284, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 614, 615, 616, 617, 618, 619, 620, 621, 622, 623});
    private static final java.util.BitSet DEV44_R10_IDS_5 = dev44R10Bits(new int[]{128, 129, 130, 131, 132, 134, 135, 136, 137, 138, 139, 150});
    private static final java.util.BitSet DEV44_R10_IDS_6 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 372, 373, 374, 375, 378, 379, 389, 390, 391, 490, 491, 492, 730, 731, 732, 733, 734, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 746, 747, 1008, 1012, 1013, 1014, 1018, 1019, 1020, 1022, 1023, 1056, 1057, 1061, 1062, 1063, 1138, 1139, 1140, 1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1166, 1167, 1168});
    private static final java.util.BitSet DEV44_R10_IDS_7 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 372, 373, 374, 375, 378, 379, 389, 390, 391, 490, 491, 729, 730, 731, 732, 733, 734, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 746, 1007, 1011, 1012, 1013, 1017, 1018, 1019, 1021, 1022, 1055, 1056, 1057, 1093, 1094, 1095, 1096, 1097, 1098, 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1175, 1176, 1177, 1178});
    private static final java.util.BitSet DEV44_R10_IDS_8 = dev44R10Bits(new int[]{7, 28, 50, 51, 52, 53, 54, 55});
    private static final java.util.BitSet DEV44_R10_IDS_9 = dev44R10Bits(new int[]{15, 16, 17, 18, 73, 74, 76, 77, 78, 138, 139, 140, 204, 205, 206, 211, 212, 213, 214, 215, 217, 218, 219, 220, 222, 223, 224, 225, 226, 237, 238, 239, 244, 245, 246, 247, 248, 250, 251, 252, 253, 255, 256, 257, 258, 259, 357, 360});
    private static final java.util.BitSet DEV44_R10_IDS_10 = dev44R10Bits(new int[]{18, 22, 23, 24, 25, 26, 27, 31, 32, 33, 34, 35, 206, 207, 208, 209, 211, 212, 213, 214, 215, 216, 217, 232, 233, 234, 235, 236, 237, 240, 241, 246, 247, 248, 249, 250, 251, 252, 287, 340, 341, 342, 343, 344, 345, 346, 350, 351, 352, 353, 354, 355, 356, 357, 359, 360, 361, 362, 363});
    private static final java.util.BitSet DEV44_R10_IDS_11 = dev44R10Bits(new int[]{77, 82, 84, 86, 87, 145, 149, 157, 162, 276, 279, 283, 290, 292, 296, 300, 308, 311, 313, 315, 317, 319, 321, 322, 326, 327, 329, 331, 334, 342, 387, 388});
    private static final java.util.BitSet DEV44_R10_IDS_12 = dev44R10Bits(new int[]{36, 37, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 53, 54, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 411, 412, 413, 414, 415, 416, 417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427, 428, 429, 430, 431, 432, 433, 434, 435, 436, 438});
    private static final java.util.BitSet DEV44_R10_IDS_13 = dev44R10Bits(new int[]{35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 49, 50, 320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 386, 387, 388, 389, 390, 391, 392, 393, 394, 395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 413});
    private static final java.util.BitSet DEV44_R10_IDS_14 = dev44R10Bits(new int[]{31, 32, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 311, 312, 313, 314, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 394, 397, 398});
    private static final java.util.BitSet DEV44_R10_IDS_15 = dev44R10Bits(new int[]{20, 21, 22, 45, 46, 47, 59, 172, 175, 178, 179, 184, 185});
    private static final java.util.BitSet DEV44_R10_IDS_16 = dev44R10Bits(new int[]{62, 64, 65, 72, 78, 86, 87, 92, 96, 97, 98, 100, 101, 102, 103});
    private static final java.util.BitSet DEV44_R10_IDS_17 = dev44R10Bits(new int[]{25, 27, 28, 29, 30, 34, 35, 36, 37, 50, 51, 52, 53, 54, 55, 65, 66, 67, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 379, 380, 381, 382, 383, 384});
    private static final java.util.BitSet DEV44_R10_IDS_18 = dev44R10Bits(new int[]{82, 83, 119, 120, 121, 122, 123, 124});
    private static final java.util.BitSet DEV44_R10_IDS_19 = dev44R10Bits(new int[]{39, 48, 49, 50, 52, 73, 74, 75, 77, 78, 79, 80, 81, 82, 83, 84, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109});
    private static final java.util.BitSet DEV44_R10_IDS_20 = dev44R10Bits(new int[]{57, 59, 60, 61, 62, 63, 64, 72, 73, 74, 75, 76, 93, 94, 146, 174, 197});
    private static final java.util.BitSet DEV44_R10_IDS_21 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 22, 187, 194, 278, 279, 280, 285, 287});
    private static final java.util.BitSet DEV44_R10_IDS_22 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 24, 190, 197, 280});
    private static final java.util.BitSet DEV44_R10_IDS_23 = dev44R10Bits(new int[]{390, 391, 392, 419, 420, 421, 422, 442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461, 462, 463, 464, 465, 466, 467, 468, 469, 470, 471, 472, 473, 474, 475, 476, 477, 478, 479, 480, 481, 482, 483, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 494, 495, 496, 497, 498, 499, 500, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528, 529, 530, 531, 532, 533, 534, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 545, 546, 547, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 579, 580, 581, 582, 583, 584, 585, 586, 587, 588, 589, 590, 591, 592, 593, 594, 595, 596, 597, 598, 671, 672, 673, 674, 675, 676, 677, 678, 679, 681, 897, 898, 899});
    private static final java.util.BitSet DEV44_R10_IDS_24 = dev44R10Bits(new int[]{25, 27, 28, 29, 30, 31, 32, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 612, 614, 616, 617, 618, 619});
    private static final java.util.BitSet DEV44_R10_IDS_25 = dev44R10Bits(new int[]{420, 422, 431, 432, 434, 435, 441, 442, 443, 444, 445, 446, 453, 455, 456, 457, 459, 460, 461, 462, 463, 478, 479, 480, 482, 484, 485, 488, 489, 490, 491, 492, 493, 494, 495, 498, 499, 500, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528, 529, 530, 531, 532, 533, 534, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 545, 546, 547, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 579, 580, 581, 582, 583, 584, 585, 586, 587, 588, 589, 590, 591, 592, 593, 594, 595, 596, 597, 598, 599, 600, 601, 602, 603, 604, 605, 606, 607, 608, 609, 610, 611, 612, 613, 614, 615, 616, 617, 618, 619, 620, 621, 622, 623, 624, 625, 626, 627, 628, 629, 630, 631, 632, 633, 634, 635, 636, 637, 638, 639, 640, 641, 642, 662, 663, 667, 669, 674, 675, 676, 677, 678, 679, 680, 681, 682, 683, 684, 685, 688, 724, 725, 726, 727});
    private static final java.util.BitSet DEV44_R10_IDS_26 = dev44R10Bits(new int[]{43, 44});
    private static final java.util.BitSet DEV44_R10_IDS_27 = dev44R10Bits(new int[]{11, 14, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 67, 76, 77, 82, 83, 84, 85, 86, 87, 88, 93, 94, 117, 118, 119, 120, 121, 122, 123, 124, 125, 194, 209, 210, 212, 213, 216, 217, 218, 222, 277});
    private static final java.util.BitSet DEV44_R10_IDS_28 = dev44R10Bits(new int[]{103, 104, 106, 107, 112, 127, 128, 129, 130, 153, 157, 158, 159, 160, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 196, 197, 198});
    private static final java.util.BitSet DEV44_R10_IDS_29 = dev44R10Bits(new int[]{1, 327, 328, 329, 330, 331, 332, 333, 334, 335});
    private static final java.util.BitSet DEV44_R10_IDS_30 = dev44R10Bits(new int[]{62, 63, 72, 73, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 225, 228, 229, 230, 232, 233, 234, 236, 237, 241, 242, 243, 244, 245, 246, 247, 248, 249});
    private static final java.util.BitSet DEV44_R10_IDS_31 = dev44R10Bits(new int[]{59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 156, 157, 158, 160, 161, 162});
    private static final java.util.BitSet DEV44_R10_IDS_32 = dev44R10Bits(new int[]{113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147});
    private static final java.util.BitSet DEV44_R10_IDS_33 = dev44R10Bits(new int[]{91, 94, 96, 99, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427, 428, 429, 430, 431, 432, 433, 434, 435, 436, 437, 438, 439, 440, 441, 442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461});
    private static final java.util.BitSet DEV44_R10_IDS_34 = dev44R10Bits(new int[]{91, 94, 96, 99, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333, 334, 335, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 435, 436, 437, 438, 439, 440, 441, 442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461, 462, 509});
    private static final java.util.BitSet DEV44_R10_IDS_35 = dev44R10Bits(new int[]{72, 75, 80, 85, 90, 96, 97, 98, 99});
    private static final java.util.BitSet DEV44_R10_IDS_36 = dev44R10Bits(new int[]{179, 180, 186, 187, 189, 190, 195, 201, 413, 516});
    private static final java.util.BitSet DEV44_R10_IDS_37 = dev44R10Bits(new int[]{53, 66, 67, 68, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144});
    private static final java.util.BitSet DEV44_R10_IDS_38 = dev44R10Bits(new int[]{34, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 52, 53, 236, 238, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289, 290, 291, 292, 293, 294});
    private static final java.util.BitSet DEV44_R10_IDS_39 = dev44R10Bits(new int[]{3, 5, 6, 7, 8, 35, 58, 59});
    private static final java.util.BitSet DEV44_R10_IDS_40 = dev44R10Bits(new int[]{5, 6, 7, 8, 9, 10});
    private static final java.util.BitSet DEV44_R10_IDS_41 = dev44R10Bits(new int[]{18, 19, 20, 24, 26, 30, 32, 33, 34, 35, 46, 67, 68, 72});
    private static final java.util.BitSet DEV44_R10_IDS_42 = dev44R10Bits(new int[]{48, 49, 50, 51, 53});
    private static final java.util.BitSet DEV44_R10_IDS_43 = dev44R10Bits(new int[]{2, 8, 15, 18, 23, 24, 25, 26, 27, 28, 29, 30, 31, 85, 95, 96, 97, 98, 132, 133, 144, 145, 148, 154, 155, 156});
    private static final java.util.BitSet DEV44_R13_ROD_0 = dev44R10Bits(new int[]{29, 30, 31, 32});
    private static final java.util.BitSet DEV44_R13_PISTON_0 = dev44R10Bits(new int[]{17, 19, 27, 28});
    private static final java.util.BitSet DEV44_R13_CRANK_0 = dev44R10Bits(new int[]{21, 22, 23, 24, 25, 26});
    private static final java.util.BitSet DEV44_R13_ROD_1 = dev44R10Bits(new int[]{62, 63, 64, 65, 66, 67, 81});
    private static final java.util.BitSet DEV44_R13_PISTON_1 = dev44R10Bits(new int[]{8});
    private static final java.util.BitSet DEV44_R13_CRANK_1 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_2 = dev44R10Bits(new int[]{38, 39, 40, 56, 57, 59, 87, 88, 89, 91, 295, 296, 301, 303, 304, 305, 306, 307, 308, 311, 312, 313, 315, 317, 334, 335, 338, 341, 342, 343, 344, 347, 348, 349, 350, 351, 352, 354, 355, 356, 357, 358, 359, 360, 361, 363, 364, 366});
    private static final java.util.BitSet DEV44_R13_PISTON_2 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_2 = dev44R10Bits(new int[]{35, 36, 37, 53, 54, 55, 84, 85, 86, 90, 294, 297, 298, 299, 302, 309, 310, 314, 316, 318, 319, 336, 337, 339, 340, 345, 346, 353, 362, 365});
    private static final java.util.BitSet DEV44_R13_ROD_3 = dev44R10Bits(new int[]{68, 71, 145, 146, 252, 253});
    private static final java.util.BitSet DEV44_R13_PISTON_3 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_3 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_4 = dev44R10Bits(new int[]{31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 45, 46, 47, 342, 343, 344, 345, 349, 350, 352, 353, 354, 355, 356, 357, 548, 549, 550, 552, 553, 554, 558, 559, 561, 562, 563, 564, 565, 566, 567, 568, 570, 571, 572, 573, 614, 615, 616, 621, 622});
    private static final java.util.BitSet DEV44_R13_PISTON_4 = dev44R10Bits(new int[]{273, 274, 275, 276, 277, 278, 279, 280, 281, 283, 284});
    private static final java.util.BitSet DEV44_R13_CRANK_4 = dev44R10Bits(new int[]{36, 42, 346, 347, 348, 351, 358, 359, 360, 361, 362, 551, 555, 556, 557, 560, 569, 574, 575, 576, 617, 618, 619, 620, 623});
    private static final java.util.BitSet DEV44_R13_ROD_5 = dev44R10Bits(new int[]{132, 134, 136, 139, 150});
    private static final java.util.BitSet DEV44_R13_PISTON_5 = dev44R10Bits(new int[]{135, 137, 138});
    private static final java.util.BitSet DEV44_R13_CRANK_5 = dev44R10Bits(new int[]{128, 129, 130, 131});
    private static final java.util.BitSet DEV44_R13_ROD_6 = dev44R10Bits(new int[]{348, 372, 378, 391, 490, 491, 492, 730, 734, 747, 1008, 1012, 1013, 1014, 1018, 1019, 1020, 1022, 1023, 1056, 1057, 1061, 1062, 1063});
    private static final java.util.BitSet DEV44_R13_PISTON_6 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148});
    private static final java.util.BitSet DEV44_R13_CRANK_6 = dev44R10Bits(new int[]{349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 373, 374, 375, 379, 389, 390, 731, 732, 733, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 746, 1138, 1139, 1140, 1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1166, 1167, 1168});
    private static final java.util.BitSet DEV44_R13_ROD_7 = dev44R10Bits(new int[]{348, 372, 378, 391, 490, 491, 729, 733, 746, 1007, 1011, 1012, 1013, 1017, 1018, 1019, 1021, 1022, 1055, 1056, 1057});
    private static final java.util.BitSet DEV44_R13_PISTON_7 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148});
    private static final java.util.BitSet DEV44_R13_CRANK_7 = dev44R10Bits(new int[]{349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 373, 374, 375, 379, 389, 390, 730, 731, 732, 734, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 1093, 1094, 1095, 1096, 1097, 1098, 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1175, 1176, 1177, 1178});
    private static final java.util.BitSet DEV44_R13_ROD_8 = dev44R10Bits(new int[]{50, 51, 52, 53, 54, 55});
    private static final java.util.BitSet DEV44_R13_PISTON_8 = dev44R10Bits(new int[]{7, 28});
    private static final java.util.BitSet DEV44_R13_CRANK_8 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_9 = dev44R10Bits(new int[]{204, 205, 206, 211, 215, 217, 218, 219, 220, 222, 223, 224, 225, 226, 237, 238, 239, 244, 248, 250, 251, 252, 253, 255, 256, 257, 258, 259});
    private static final java.util.BitSet DEV44_R13_PISTON_9 = dev44R10Bits(new int[]{15, 16, 17, 18, 73, 74, 76, 77, 78, 138, 139, 140, 357, 360});
    private static final java.util.BitSet DEV44_R13_CRANK_9 = dev44R10Bits(new int[]{212, 213, 214, 245, 246, 247});
    private static final java.util.BitSet DEV44_R13_ROD_10 = dev44R10Bits(new int[]{206, 207, 208, 209, 211, 212, 214, 216, 217, 233, 236, 237, 240, 241, 246, 247, 249, 250, 251, 287, 341, 344, 345, 346, 350, 351, 353, 354, 355, 356, 359, 362, 363});
    private static final java.util.BitSet DEV44_R13_PISTON_10 = dev44R10Bits(new int[]{18, 22, 23, 24, 25, 26, 27, 31, 32, 33, 34, 35});
    private static final java.util.BitSet DEV44_R13_CRANK_10 = dev44R10Bits(new int[]{213, 215, 232, 234, 235, 248, 252, 340, 342, 343, 352, 357, 360, 361});
    private static final java.util.BitSet DEV44_R13_ROD_11 = dev44R10Bits(new int[]{82, 86, 87, 276, 279, 283, 290, 292, 300, 308, 313, 317, 319, 321, 322, 326, 331, 334, 342, 387, 388});
    private static final java.util.BitSet DEV44_R13_PISTON_11 = dev44R10Bits(new int[]{145, 149, 157, 162});
    private static final java.util.BitSet DEV44_R13_CRANK_11 = dev44R10Bits(new int[]{77, 84, 296, 311, 315, 327, 329});
    private static final java.util.BitSet DEV44_R13_ROD_12 = dev44R10Bits(new int[]{36, 37, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 53, 54, 411, 412, 413, 414, 419, 420, 422, 423, 424, 425, 426, 427, 432, 433, 434, 435, 436});
    private static final java.util.BitSet DEV44_R13_PISTON_12 = dev44R10Bits(new int[]{345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356});
    private static final java.util.BitSet DEV44_R13_CRANK_12 = dev44R10Bits(new int[]{415, 416, 417, 418, 421, 428, 429, 430, 431, 438});
    private static final java.util.BitSet DEV44_R13_ROD_13 = dev44R10Bits(new int[]{35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 49, 50, 386, 387, 388, 389, 394, 395, 397, 398, 399, 400, 401, 402, 407, 408, 409, 410});
    private static final java.util.BitSet DEV44_R13_PISTON_13 = dev44R10Bits(new int[]{320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331});
    private static final java.util.BitSet DEV44_R13_CRANK_13 = dev44R10Bits(new int[]{390, 391, 392, 393, 396, 403, 404, 405, 406, 413});
    private static final java.util.BitSet DEV44_R13_ROD_14 = dev44R10Bits(new int[]{31, 32, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 371, 372, 373, 374, 378, 379, 381, 382, 383, 384, 385, 386, 391, 392, 393, 394});
    private static final java.util.BitSet DEV44_R13_PISTON_14 = dev44R10Bits(new int[]{311, 312, 313, 314});
    private static final java.util.BitSet DEV44_R13_CRANK_14 = dev44R10Bits(new int[]{375, 376, 377, 380, 387, 388, 389, 390, 397, 398});
    private static final java.util.BitSet DEV44_R13_ROD_15 = dev44R10Bits(new int[]{22, 47, 59, 178, 184});
    private static final java.util.BitSet DEV44_R13_PISTON_15 = dev44R10Bits(new int[]{172, 185});
    private static final java.util.BitSet DEV44_R13_CRANK_15 = dev44R10Bits(new int[]{20, 21, 45, 46, 175, 179});
    private static final java.util.BitSet DEV44_R13_ROD_16 = dev44R10Bits(new int[]{72, 78, 87, 92, 96, 97, 98, 100, 103});
    private static final java.util.BitSet DEV44_R13_PISTON_16 = dev44R10Bits(new int[]{101, 102});
    private static final java.util.BitSet DEV44_R13_CRANK_16 = dev44R10Bits(new int[]{62, 64, 65, 86});
    private static final java.util.BitSet DEV44_R13_ROD_17 = dev44R10Bits(new int[]{29, 30, 35, 36, 51, 53, 65, 66, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 379, 380, 381, 382, 383, 384});
    private static final java.util.BitSet DEV44_R13_PISTON_17 = dev44R10Bits(new int[]{34, 55});
    private static final java.util.BitSet DEV44_R13_CRANK_17 = dev44R10Bits(new int[]{25, 27, 28, 37, 50, 52, 54, 67});
    private static final java.util.BitSet DEV44_R13_ROD_18 = dev44R10Bits(new int[]{82, 83});
    private static final java.util.BitSet DEV44_R13_PISTON_18 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_18 = dev44R10Bits(new int[]{119, 120, 121, 122, 123, 124});
    private static final java.util.BitSet DEV44_R13_ROD_19 = dev44R10Bits(new int[]{73, 75, 77, 78, 79, 80, 81, 82, 83, 99, 100, 101, 102, 103, 104, 106, 107, 108, 109});
    private static final java.util.BitSet DEV44_R13_PISTON_19 = dev44R10Bits(new int[]{39, 74});
    private static final java.util.BitSet DEV44_R13_CRANK_19 = dev44R10Bits(new int[]{48, 49, 50, 52, 84, 105});
    private static final java.util.BitSet DEV44_R13_ROD_20 = dev44R10Bits(new int[]{59, 60, 61, 62, 63, 64, 72, 73, 74, 75, 76, 93, 146});
    private static final java.util.BitSet DEV44_R13_PISTON_20 = dev44R10Bits(new int[]{57, 174, 197});
    private static final java.util.BitSet DEV44_R13_CRANK_20 = dev44R10Bits(new int[]{94});
    private static final java.util.BitSet DEV44_R13_ROD_21 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 22, 278});
    private static final java.util.BitSet DEV44_R13_PISTON_21 = dev44R10Bits(new int[]{187, 194, 279, 280, 285, 287});
    private static final java.util.BitSet DEV44_R13_CRANK_21 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_22 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 24, 280});
    private static final java.util.BitSet DEV44_R13_PISTON_22 = dev44R10Bits(new int[]{190, 197});
    private static final java.util.BitSet DEV44_R13_CRANK_22 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_23 = dev44R10Bits(new int[]{442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461, 462, 463, 465, 468, 469, 470, 471, 472, 473, 474, 475, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 502, 503, 504, 529, 530, 531, 547, 548, 549, 556, 557, 558, 562, 563, 564, 579, 580, 581, 593, 594, 595, 596, 897, 898, 899});
    private static final java.util.BitSet DEV44_R13_PISTON_23 = dev44R10Bits(new int[]{390, 391, 392, 419, 420, 421, 422});
    private static final java.util.BitSet DEV44_R13_CRANK_23 = dev44R10Bits(new int[]{464, 466, 467, 476, 477, 478, 479, 480, 481, 482, 483, 494, 495, 496, 497, 498, 499, 500, 501, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528, 532, 533, 534, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 545, 546, 550, 551, 552, 553, 554, 555, 559, 560, 561, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 582, 583, 584, 585, 586, 587, 588, 589, 590, 591, 592, 597, 598, 671, 672, 673, 674, 675, 676, 677, 678, 679, 681});
    private static final java.util.BitSet DEV44_R13_ROD_24 = dev44R10Bits(new int[]{25, 27, 28, 29, 31, 32, 37, 39, 40, 41, 43, 44, 616, 617, 618, 619});
    private static final java.util.BitSet DEV44_R13_PISTON_24 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_24 = dev44R10Bits(new int[]{30, 38, 42, 45, 46, 612, 614});
    private static final java.util.BitSet DEV44_R13_ROD_25 = dev44R10Bits(new int[]{478, 479, 480, 482, 484, 485, 488, 489, 490, 491, 492, 493, 494, 495, 498, 499, 500, 517, 518, 519, 524, 526, 533, 534, 535, 544, 545, 546, 560, 561, 562, 567, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 579, 590, 591, 592, 593, 594, 595, 596, 597, 598, 599, 600, 601, 602, 603, 604, 605, 606, 607, 608, 609, 610, 611, 614, 615, 617, 640, 641, 642, 662, 663, 667, 669, 688});
    private static final java.util.BitSet DEV44_R13_PISTON_25 = dev44R10Bits(new int[]{420, 422, 431, 432, 434, 435, 441, 442, 443, 444, 445, 446, 453, 455, 456, 457, 459, 460, 461, 462, 463});
    private static final java.util.BitSet DEV44_R13_CRANK_25 = dev44R10Bits(new int[]{501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 520, 521, 522, 523, 525, 527, 528, 529, 530, 531, 532, 536, 537, 538, 539, 540, 541, 542, 543, 547, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 563, 564, 565, 566, 568, 580, 581, 582, 583, 584, 585, 586, 587, 588, 589, 612, 613, 616, 618, 619, 620, 621, 622, 623, 624, 625, 626, 627, 628, 629, 630, 631, 632, 633, 634, 635, 636, 637, 638, 639, 674, 675, 676, 677, 678, 679, 680, 681, 682, 683, 684, 685, 724, 725, 726, 727});
    private static final java.util.BitSet DEV44_R13_ROD_26 = dev44R10Bits(new int[]{43, 44});
    private static final java.util.BitSet DEV44_R13_PISTON_26 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_26 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_27 = dev44R10Bits(new int[]{38, 39, 40, 43, 44, 45, 67, 76, 77, 82, 83, 84, 85, 87, 88, 93, 94, 117, 118, 119, 120, 121, 122, 123, 124, 125, 194, 277});
    private static final java.util.BitSet DEV44_R13_PISTON_27 = dev44R10Bits(new int[]{11, 14, 209, 210, 212, 213, 216, 217, 218, 222});
    private static final java.util.BitSet DEV44_R13_CRANK_27 = dev44R10Bits(new int[]{37, 41, 42, 46, 86});
    private static final java.util.BitSet DEV44_R13_ROD_28 = dev44R10Bits(new int[]{103, 104, 112, 127, 128, 129, 130, 153, 157, 158, 159, 160, 169, 171, 173, 174, 175, 177, 178, 179, 181, 186, 187, 188, 189, 190, 191, 196, 198});
    private static final java.util.BitSet DEV44_R13_PISTON_28 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_28 = dev44R10Bits(new int[]{106, 107, 170, 172, 176, 180, 182, 183, 184, 185, 197});
    private static final java.util.BitSet DEV44_R13_ROD_29 = dev44R10Bits(new int[]{1, 329, 333, 334});
    private static final java.util.BitSet DEV44_R13_PISTON_29 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_29 = dev44R10Bits(new int[]{327, 328, 330, 331, 332, 335});
    private static final java.util.BitSet DEV44_R13_ROD_30 = dev44R10Bits(new int[]{206, 207, 209, 210, 211, 212, 214, 215, 216, 217, 218, 219, 220, 221, 222, 225, 228, 229, 230, 232, 233, 234, 236, 237, 241, 242, 243, 244, 245, 246, 249});
    private static final java.util.BitSet DEV44_R13_PISTON_30 = dev44R10Bits(new int[]{62, 63, 72, 73, 248});
    private static final java.util.BitSet DEV44_R13_CRANK_30 = dev44R10Bits(new int[]{208, 213, 247});
    private static final java.util.BitSet DEV44_R13_ROD_31 = dev44R10Bits(new int[]{59, 61, 62, 63, 65, 66, 67, 68, 69, 70, 72, 73, 74, 75, 77, 79, 80, 81, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93});
    private static final java.util.BitSet DEV44_R13_PISTON_31 = dev44R10Bits(new int[]{156, 157, 158, 160, 161, 162});
    private static final java.util.BitSet DEV44_R13_CRANK_31 = dev44R10Bits(new int[]{60, 64, 71, 76, 78, 82});
    private static final java.util.BitSet DEV44_R13_ROD_32 = dev44R10Bits(new int[]{113, 115, 116, 117, 119, 120, 121, 122, 123, 125, 126, 127, 128, 129, 131, 133, 134, 135, 137, 138, 139, 140, 141, 145, 146, 147});
    private static final java.util.BitSet DEV44_R13_PISTON_32 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_32 = dev44R10Bits(new int[]{114, 118, 124, 130, 132, 136, 142, 143, 144});
    private static final java.util.BitSet DEV44_R13_ROD_33 = dev44R10Bits(new int[]{398, 399, 400, 401, 402, 403, 404, 408, 409, 411, 412, 413, 414, 415, 416, 417, 418, 419, 423, 424, 425, 427, 428, 429, 430, 431, 433, 434, 435, 437, 438, 439, 442, 443, 445, 446, 447, 448, 449, 450, 451, 452, 454, 455, 456, 457});
    private static final java.util.BitSet DEV44_R13_PISTON_33 = dev44R10Bits(new int[]{91, 94, 96, 99});
    private static final java.util.BitSet DEV44_R13_CRANK_33 = dev44R10Bits(new int[]{405, 406, 407, 410, 420, 421, 422, 426, 432, 436, 440, 441, 444, 453, 458, 459, 460, 461});
    private static final java.util.BitSet DEV44_R13_ROD_34 = dev44R10Bits(new int[]{323, 324, 325, 326, 327, 328, 329, 333, 334, 340, 341, 342, 343, 344, 345, 346, 347, 348, 405, 406, 407, 408, 409, 410, 411, 412, 413, 435, 436, 437, 439, 440, 441, 444, 445, 447, 448, 449, 450, 451, 452, 453, 454, 456, 457, 458, 459});
    private static final java.util.BitSet DEV44_R13_PISTON_34 = dev44R10Bits(new int[]{91, 94, 96, 99});
    private static final java.util.BitSet DEV44_R13_CRANK_34 = dev44R10Bits(new int[]{330, 331, 332, 335, 349, 350, 351, 414, 438, 442, 443, 446, 455, 460, 461, 462, 509});
    private static final java.util.BitSet DEV44_R13_ROD_35 = dev44R10Bits(new int[]{72, 96, 97, 98});
    private static final java.util.BitSet DEV44_R13_PISTON_35 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_35 = dev44R10Bits(new int[]{75, 80, 85, 90, 99});
    private static final java.util.BitSet DEV44_R13_ROD_36 = dev44R10Bits(new int[]{179, 180, 186, 187, 189, 190, 195, 201, 413, 516});
    private static final java.util.BitSet DEV44_R13_PISTON_36 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_36 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_37 = dev44R10Bits(new int[]{121, 122, 123, 124, 126, 127, 128, 129, 131, 132, 133, 134, 136, 137, 138, 139, 140, 142, 143, 144});
    private static final java.util.BitSet DEV44_R13_PISTON_37 = dev44R10Bits(new int[]{53, 66, 67, 68});
    private static final java.util.BitSet DEV44_R13_CRANK_37 = dev44R10Bits(new int[]{125, 130, 135, 141});
    private static final java.util.BitSet DEV44_R13_ROD_38 = dev44R10Bits(new int[]{34, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 52, 53, 275, 276, 277, 278, 282, 283, 285, 286, 287, 288, 289, 290});
    private static final java.util.BitSet DEV44_R13_PISTON_38 = dev44R10Bits(new int[]{236, 238});
    private static final java.util.BitSet DEV44_R13_CRANK_38 = dev44R10Bits(new int[]{279, 280, 281, 284, 291, 292, 293, 294});
    private static final java.util.BitSet DEV44_R13_ROD_39 = dev44R10Bits(new int[]{3, 6, 8, 35, 58, 59});
    private static final java.util.BitSet DEV44_R13_PISTON_39 = dev44R10Bits(new int[]{5, 7});
    private static final java.util.BitSet DEV44_R13_CRANK_39 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_40 = dev44R10Bits(new int[]{9, 10});
    private static final java.util.BitSet DEV44_R13_PISTON_40 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_40 = dev44R10Bits(new int[]{5, 6, 7, 8});
    private static final java.util.BitSet DEV44_R13_ROD_41 = dev44R10Bits(new int[]{20, 24, 32, 33, 46, 68});
    private static final java.util.BitSet DEV44_R13_PISTON_41 = dev44R10Bits(new int[]{34, 35});
    private static final java.util.BitSet DEV44_R13_CRANK_41 = dev44R10Bits(new int[]{18, 19, 26, 30, 67, 72});
    private static final java.util.BitSet DEV44_R13_ROD_42 = dev44R10Bits(new int[]{48, 49, 50, 51, 53});
    private static final java.util.BitSet DEV44_R13_PISTON_42 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_42 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_43 = dev44R10Bits(new int[]{24, 26, 85, 96, 97, 98, 132, 133, 154, 156});
    private static final java.util.BitSet DEV44_R13_PISTON_43 = dev44R10Bits(new int[]{15, 18, 23, 144, 145, 148});
    private static final java.util.BitSet DEV44_R13_CRANK_43 = dev44R10Bits(new int[]{2, 8, 25, 27, 28, 29, 30, 31, 95, 155});

    // DEV44 R13: separate, reversible linkage motion *preview*.
    // Original authored mesh surfaces are reused and not replaced by inferred boxes.
    // Preview is OFF until run/DEV44-R13-MOTION-PREVIEW-ON.txt contains ON.
    private static boolean dev44R13PreviewOn() {
        try {
            java.nio.file.Path p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV44-R13-MOTION-PREVIEW-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) { return false; }
    }
    private static boolean dev44R13Protected(String name) {
        // Never animate previously accepted mechanisms in an experimental preview.
        return name.equals("locomotive_steam_vb_shay")
                || name.equals("locomotive_steam_alco_sc4");
    }
    private static int dev44R13Role(String source, int chunk) {
        String name = source.substring(source.lastIndexOf('/')+1).replace(".tcm", "");
        if (dev44R13Protected(name)) return 0;
        if (name.equals("locomotive_steam_262t")) { if (DEV44_R13_ROD_0.get(chunk)) return 1; if (DEV44_R13_PISTON_0.get(chunk)) return 2; if (DEV44_R13_CRANK_0.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_4_4_0")) { if (DEV44_R13_ROD_1.get(chunk)) return 1; if (DEV44_R13_PISTON_1.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_a4_mallard")) { if (DEV44_R13_ROD_2.get(chunk)) return 1; if (DEV44_R13_CRANK_2.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_adler")) { if (DEV44_R13_ROD_3.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_alco_sc4")) { if (DEV44_R13_ROD_4.get(chunk)) return 1; if (DEV44_R13_PISTON_4.get(chunk)) return 2; if (DEV44_R13_CRANK_4.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_alice_0_4_0")) { if (DEV44_R13_ROD_5.get(chunk)) return 1; if (DEV44_R13_PISTON_5.get(chunk)) return 2; if (DEV44_R13_CRANK_5.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_berkshire_1225")) { if (DEV44_R13_ROD_6.get(chunk)) return 1; if (DEV44_R13_PISTON_6.get(chunk)) return 2; if (DEV44_R13_CRANK_6.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_berkshire_765")) { if (DEV44_R13_ROD_7.get(chunk)) return 1; if (DEV44_R13_PISTON_7.get(chunk)) return 2; if (DEV44_R13_CRANK_7.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_br80")) { if (DEV44_R13_ROD_8.get(chunk)) return 1; if (DEV44_R13_PISTON_8.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_br_black_5")) { if (DEV44_R13_ROD_9.get(chunk)) return 1; if (DEV44_R13_PISTON_9.get(chunk)) return 2; if (DEV44_R13_CRANK_9.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_br_britannia")) { if (DEV44_R13_ROD_10.get(chunk)) return 1; if (DEV44_R13_PISTON_10.get(chunk)) return 2; if (DEV44_R13_CRANK_10.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c11")) { if (DEV44_R13_ROD_11.get(chunk)) return 1; if (DEV44_R13_PISTON_11.get(chunk)) return 2; if (DEV44_R13_CRANK_11.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c41")) { if (DEV44_R13_ROD_12.get(chunk)) return 1; if (DEV44_R13_PISTON_12.get(chunk)) return 2; if (DEV44_R13_CRANK_12.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c41_080")) { if (DEV44_R13_ROD_13.get(chunk)) return 1; if (DEV44_R13_PISTON_13.get(chunk)) return 2; if (DEV44_R13_CRANK_13.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c41t")) { if (DEV44_R13_ROD_14.get(chunk)) return 1; if (DEV44_R13_PISTON_14.get(chunk)) return 2; if (DEV44_R13_CRANK_14.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_climax")) { if (DEV44_R13_ROD_15.get(chunk)) return 1; if (DEV44_R13_PISTON_15.get(chunk)) return 2; if (DEV44_R13_CRANK_15.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_climax_2")) { if (DEV44_R13_ROD_16.get(chunk)) return 1; if (DEV44_R13_PISTON_16.get(chunk)) return 2; if (DEV44_R13_CRANK_16.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_coronation_class")) { if (DEV44_R13_ROD_17.get(chunk)) return 1; if (DEV44_R13_PISTON_17.get(chunk)) return 2; if (DEV44_R13_CRANK_17.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_fowler")) { if (DEV44_R13_ROD_18.get(chunk)) return 1; if (DEV44_R13_CRANK_18.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_gs4")) { if (DEV44_R13_ROD_19.get(chunk)) return 1; if (DEV44_R13_PISTON_19.get(chunk)) return 2; if (DEV44_R13_CRANK_19.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_gwr_101_class")) { if (DEV44_R13_ROD_20.get(chunk)) return 1; if (DEV44_R13_PISTON_20.get(chunk)) return 2; if (DEV44_R13_CRANK_20.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_gwr_42xx")) { if (DEV44_R13_ROD_21.get(chunk)) return 1; if (DEV44_R13_PISTON_21.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_gwr_72xx")) { if (DEV44_R13_ROD_22.get(chunk)) return 1; if (DEV44_R13_PISTON_22.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_hall_class")) { if (DEV44_R13_ROD_23.get(chunk)) return 1; if (DEV44_R13_PISTON_23.get(chunk)) return 2; if (DEV44_R13_CRANK_23.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_j50")) { if (DEV44_R13_ROD_24.get(chunk)) return 1; if (DEV44_R13_CRANK_24.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_king_class")) { if (DEV44_R13_ROD_25.get(chunk)) return 1; if (DEV44_R13_PISTON_25.get(chunk)) return 2; if (DEV44_R13_CRANK_25.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_lssp7")) { if (DEV44_R13_ROD_26.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_midland_compound")) { if (DEV44_R13_ROD_27.get(chunk)) return 1; if (DEV44_R13_PISTON_27.get(chunk)) return 2; if (DEV44_R13_CRANK_27.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_milw_class_a")) { if (DEV44_R13_ROD_28.get(chunk)) return 1; if (DEV44_R13_CRANK_28.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_pannier")) { if (DEV44_R13_ROD_29.get(chunk)) return 1; if (DEV44_R13_CRANK_29.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_pe")) { if (DEV44_R13_ROD_30.get(chunk)) return 1; if (DEV44_R13_PISTON_30.get(chunk)) return 2; if (DEV44_R13_CRANK_30.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_rw_type_2")) { if (DEV44_R13_ROD_31.get(chunk)) return 1; if (DEV44_R13_PISTON_31.get(chunk)) return 2; if (DEV44_R13_CRANK_31.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_rw_type_3")) { if (DEV44_R13_ROD_32.get(chunk)) return 1; if (DEV44_R13_CRANK_32.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_s100_uk")) { if (DEV44_R13_ROD_33.get(chunk)) return 1; if (DEV44_R13_PISTON_33.get(chunk)) return 2; if (DEV44_R13_CRANK_33.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_s100_us")) { if (DEV44_R13_ROD_34.get(chunk)) return 1; if (DEV44_R13_PISTON_34.get(chunk)) return 2; if (DEV44_R13_CRANK_34.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_sentinel_y3")) { if (DEV44_R13_ROD_35.get(chunk)) return 1; if (DEV44_R13_CRANK_35.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_shay_3_truck")) { if (DEV44_R13_ROD_36.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_skookum")) { if (DEV44_R13_ROD_37.get(chunk)) return 1; if (DEV44_R13_PISTON_37.get(chunk)) return 2; if (DEV44_R13_CRANK_37.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_southern1102")) { if (DEV44_R13_ROD_38.get(chunk)) return 1; if (DEV44_R13_PISTON_38.get(chunk)) return 2; if (DEV44_R13_CRANK_38.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_u57")) { if (DEV44_R13_ROD_39.get(chunk)) return 1; if (DEV44_R13_PISTON_39.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_vb_0_4_0")) { if (DEV44_R13_ROD_40.get(chunk)) return 1; if (DEV44_R13_CRANK_40.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_vb_shay")) { if (DEV44_R13_ROD_41.get(chunk)) return 1; if (DEV44_R13_PISTON_41.get(chunk)) return 2; if (DEV44_R13_CRANK_41.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_vb_shay_2")) { if (DEV44_R13_ROD_42.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_wwcp_062t")) { if (DEV44_R13_ROD_43.get(chunk)) return 1; if (DEV44_R13_PISTON_43.get(chunk)) return 2; if (DEV44_R13_CRANK_43.get(chunk)) return 3; return 0; }
        return 0;
    }
    // DEV44 R13d: fleet-wide geometry telemetry. Diagnostic only; no wheel or linkage mutations.
    private static final java.util.Set<String> DEV44_R13D_HEADER = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long dev44R13dFrames;
    private static boolean dev44R13dOn() {
        try {
            var marker = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13D-FLEET-DIAGNOSTIC-ON.txt");
            return java.nio.file.Files.isRegularFile(marker)
                    && java.nio.file.Files.readString(marker, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) {return false;}
    }
    private static synchronized void dev44R13dLine(String name, String row) {
        try {
            var dir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13d-Fleet-Diagnostics");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(name), row + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            LOGGER.warn("[TC-DEV44-R13d] diagnostic write failed: {}", ex.toString());
        }
    }
    private void dev44R13dCapture(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13dOn() || !this.dev44R11Verified) return;
        String mesh=this.source.getPath().substring(this.source.getPath().lastIndexOf('/')+1).replace(".tcm", "");
        long frame=++dev44R13dFrames;
        boolean first=DEV44_R13D_HEADER.add(mesh);
        if (first) {
            dev44R13dLine("fleet-summary.csv", "MESH,"+mesh+",WHEELS,"+wheels.size()+",CHUNKS,"+((vertexCount+23)/24));
            dev44R13dLine(mesh+".csv", "kind,frame,chunk,role,cx,cy,cz,wheelX,wheelY,wheelZ,axis,cos,sin,nearestWheelDistance,notes");
        }
        // Initial full geometry sweep, then phase samples at deliberately bounded frequency.
        if (!first && frame % 40 != 0) return;
        int total=(this.vertexCount+23)/24;
        for(int ch=0;ch<total;ch++) {
            int role=dev44R13Role(this.source.getPath(),ch);
            if(role==0 && !first) continue;
            if(role==0 && !dev44R10Mapped(this.source.getPath(),ch)) continue;
            int start=ch*24*FLOATS_PER_VERTEX;
            int end=Math.min(vertices.length,start+24*FLOATS_PER_VERTEX);
            float x=0,y=0,zz=0;int n=0;
            for(int k=start;k<end;k+=FLOATS_PER_VERTEX){x+=vertices[k];y+=vertices[k+1];zz+=vertices[k+2];n++;}
            if(n==0) continue;
            x/=n;y/=n;zz/=n;
            var best=wheels.isEmpty()?null:wheels.get(0);
            if(best!=null) best=dev44R13NearestWheel(wheels,x,y,zz,best);
            String kind=role==0?"MAPPED_UNCLASSIFIED":"LINKAGE";
            String wheelValues=best==null?",,,,,,,":","+best.centerX()+","+best.centerY()+","+best.centerZ()+","+best.axis()+","+best.cos()+","+best.sin()+","+Math.sqrt(Math.pow(x-best.centerX(),2)+Math.pow(y-best.centerY(),2)+Math.pow(zz-best.centerZ(),2));
            dev44R13dLine(mesh+".csv",kind+","+frame+","+ch+","+role+","+x+","+y+","+zz+wheelValues+",computed-geometry-not-verified-pin");
        }
        if(first || frame%40==0) {
            for(int i=0;i<wheels.size();i++) {
                var w=wheels.get(i);
                dev44R13dLine(mesh+".csv","WHEEL,"+frame+","+i+",0,,,,"+w.centerX()+","+w.centerY()+","+w.centerZ()+","+w.axis()+","+w.cos()+","+w.sin()+",,accepted-wheel-phase");
            }
        }
    }
    // DEV44 R13j: ALCO SC4 opt-in, original authored rod mesh visibility proof.
    // Protects accepted wheel animation: never changes dynamic wheel transforms.
    private static boolean dev44R13jAlcoProofOn() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13j-ALCO-SC4-RODS-PROOF-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    // DEV44 R13k: ALCO SC4 ONLY; opt-in linkage motion PROOF, not accepted attachment solver.
    // R13h cache and accepted wheel animator are left untouched.
    private static boolean dev44R13kAlcoMotionOn() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13k-ALCO-SC4-MOTION-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) { return false; }
    }
    // DEV44 R13l: fail-closed fleet linkage review. Static attachment suggestions are NOT
    // mechanically verified; opt-in mode suppresses the rejected R13k preview, never wheels.
    private static boolean dev44R13lReviewOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13l-FLEET-REVIEW-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private boolean dev44R13lReported = false;
    private void dev44R13lReport(String meshKey, java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13lReviewOn() || dev44R13lReported || !this.dev44R11Verified) return;
        dev44R13lReported = true; // one-shot per mesh; no per-frame writes
        try {
            dev44R13hPrepare(); // already cached by accepted R13h implementation
            var dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13l-Fleet-Review");
            java.nio.file.Files.createDirectories(dir);
            var mesh=this.source.getPath();
            if (!mesh.startsWith("legacy_meshes/locomotive_steam_")) return;
            var name=mesh.substring(mesh.lastIndexOf('/')+1).replace(".tcm", "");
            var sb=new StringBuilder("mesh,chunk,role,chunkCenterX,chunkCenterY,chunkCenterZ,nearestWheelX,nearestWheelY,nearestWheelZ,centerDistance,reviewStatus\n");
            int candidates=0;
            for(int ch=0;ch<dev44R13hRoles.length;ch++) {
                int role=dev44R13hRoles[ch];
                if(role!=1 && role!=2 && role!=3) continue;
                float x=dev44R13hCx[ch],y=dev44R13hCy[ch],z=dev44R13hCz[ch];
                double best=Double.POSITIVE_INFINITY;
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec near=null;
                for(var wheel:wheels) {
                    double dx=x-wheel.centerX(),dy=y-wheel.centerY(),dz=z-wheel.centerZ();
                    double dist=dx*dx+dy*dy+dz*dz;
                    if(dist<best) {best=dist; near=wheel;}
                }
                sb.append(name).append(',').append(ch).append(',').append(role).append(',')
                  .append(x).append(',').append(y).append(',').append(z).append(',');
                if(near!=null) sb.append(near.centerX()).append(',').append(near.centerY()).append(',')
                  .append(near.centerZ()).append(',').append(Math.sqrt(best));
                else sb.append(",,,");
                sb.append(',').append(role==2?"STATIONARY_SHELL_DO_NOT_ANIMATE":
                    near==null?"NO_WHEEL_REQUIRES_REVIEW":"CANDIDATE_ONLY_ENDPOINTS_UNVERIFIED").append('\n');
                candidates++;
            }
            java.nio.file.Files.writeString(dir.resolve(name+".csv"),sb.toString(),
                 java.nio.charset.StandardCharsets.UTF_8,
                 java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[TC-DEV44-R13l-FLEET] mesh={} candidates={} wheels={} reviewed=false phase=ACCEPTED_READ_ONLY previewR13kSuppressed=true", name,candidates,wheels.size());
        } catch(Exception ex) {LOGGER.warn("[TC-DEV44-R13l-FLEET] report failed {}",ex.toString());}
    }

    // DEV44 R13m: READ-ONLY fleet rod endpoint extraction; geometric extremes are
    // hypotheses, NOT proven joints. Capture once per mesh with no per-frame writes.
    private static boolean dev44R13mCaptureOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13m-ENDPOINT-CAPTURE-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) {return false;}
    }
    private boolean dev44R13mReported = false;
    private void dev44R13mReport(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13mCaptureOn() || dev44R13mReported || !this.dev44R11Verified) return;
        String mesh=this.source.getPath();
        if (!mesh.startsWith("legacy_meshes/locomotive_steam_")) return;
        dev44R13mReported=true;
        try {
            dev44R13hPrepare();
            var dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13m-Fleet-Endpoints");
            java.nio.file.Files.createDirectories(dir);
            String name=mesh.substring(mesh.lastIndexOf('/')+1).replace(".tcm", "");
            var b=new StringBuilder("mesh,chunk,role,geometryAxis,endpointA_X,endpointA_Y,endpointA_Z,endpointB_X,endpointB_Y,endpointB_Z,span,nearestWheelA_X,nearestWheelA_Y,nearestWheelA_Z,nearestWheelB_X,nearestWheelB_Y,nearestWheelB_Z,distanceA,distanceB,status\n");
            int count=0; int total=(this.vertexCount+23)/24;
            for (int ch=0;ch<total;ch++) {
                int role=ch<dev44R13hRoles.length?dev44R13hRoles[ch]:0;
                // All mapped candidate roles plus SC4 authored rod chunks, even if role 0.
                boolean alco=name.equals("locomotive_steam_alco_sc4");
                boolean mapped=dev44R10Mapped(mesh,ch);
                if (role!=1 && role!=2 && role!=3 && !(alco && mapped)) continue;
                int lo=ch*24*FLOATS_PER_VERTEX;
                int hi=Math.min(this.vertexCount*FLOATS_PER_VERTEX,lo+24*FLOATS_PER_VERTEX);
                if (lo>=hi) continue;
                float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
                float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                float[] sum={0,0,0};int n=0;
                for (int j=lo;j+2<hi;j+=FLOATS_PER_VERTEX) {
                    for (int ax=0;ax<3;ax++) {float q=this.vertices[j+ax];
                        if (!Float.isFinite(q)) continue;
                        min[ax]=Math.min(min[ax],q);max[ax]=Math.max(max[ax],q);sum[ax]+=q;}
                    n++;
                }
                if(n==0)continue;
                float[] center={sum[0]/n,sum[1]/n,sum[2]/n};
                int axis=0;for(int a=1;a<3;a++)if(max[a]-min[a]>max[axis]-min[axis])axis=a;
                float[] a=center.clone(),c=center.clone(); a[axis]=min[axis];c[axis]=max[axis];
                var nearA=dev44R13mNearest(wheels,a);
                var nearB=dev44R13mNearest(wheels,c);
                float span=max[axis]-min[axis];
                double da=nearA==null?Double.NaN:dev44R13mDist(a,nearA);
                double db=nearB==null?Double.NaN:dev44R13mDist(c,nearB);
                String status=role==2?"FIXED_SHELL_NOT_ANIMATED":
                    nearA==null||nearB==null?"NO_WHEEL_PIN_UNVERIFIED":
                    span<0.01f?"DEGENERATE_EXTENT_UNVERIFIED":"EXTREME_POINTS_ONLY_UNVERIFIED";
                b.append(name).append(',').append(ch).append(',').append(role).append(',').append(axis);
                for(float v:a)b.append(',').append(v);
                for(float v:c)b.append(',').append(v);
                b.append(',').append(span);
                for(var w:new traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec[]{nearA,nearB}) {
                    if(w==null)b.append(",,,");
                    else b.append(',').append(w.centerX()).append(',').append(w.centerY()).append(',').append(w.centerZ());
                }
                b.append(',').append(da).append(',').append(db).append(',').append(status).append('\n');count++;
            }
            java.nio.file.Files.writeString(dir.resolve(name+".csv"),b.toString(),
                java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[TC-DEV44-R13m-ENDPOINTS] mesh={} candidateChunks={} wheels={} verifiedEndpoints=0 motionChanges=false",name,count,wheels.size());
        } catch(Exception e){LOGGER.warn("[TC-DEV44-R13m-ENDPOINTS] {} failed {}",mesh,e.toString());}
    }
    private static double dev44R13mDist(float[] p,
          traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec w) {
        return Math.sqrt(Math.pow(p[0]-w.centerX(),2)+Math.pow(p[1]-w.centerY(),2)+Math.pow(p[2]-w.centerZ(),2));
    }
    private static traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec
    dev44R13mNearest(java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels,float[] p) {
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec best=null;
        double d=Double.POSITIVE_INFINITY;
        for (var w:wheels) {double v=dev44R13mDist(p,w);if(v<d){d=v;best=w;}}
        return best;
    }

    // DEV44 R13n: conservative joint plausibility audit. A wheel CENTER is not a
    // crank PIN; no row generated here is an accepted/verified joint.


    // DEV44 R13w: Berkshire-only old-preview isolation plus phase-sampled wheel context.
    // Read only: does not select/approve crank pins or alter accepted wheel transforms.
    private static final java.util.Set<Integer> dev44R13wLoggedQuadrants =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13wOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13w-BERKSHIRE-TEST-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }

    // DEV44 R13ab (R13x switch; R13z motion preserved): opt-in Berkshire 765 RIGID rod preview. User-selected
    // mesh extrema are candidate joints; not verified mechanical crank pins.
    private static boolean dev44R13xOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13x-BERKSHIRE-ROD348-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    // DEV44 R13ad: INDEPENDENT opt-in mirrored-side rod733 trial; leaves R13ab rod348 intact.
    private static boolean dev44R13adOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ad-BERKSHIRE-ROD733-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    // R13af: BOTH-SIDE slider proof; the two selected chunks are CANDIDATES, not verified crossheads.
    private static boolean dev44R13amOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13am-BERKSHIRE-JOINT-SOLVER-AUDIT-ON.txt");
              return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13amLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13anOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13an-BERKSHIRE-ATTACHMENT-CALIBRATION-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13anLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13aoOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ao-MESH-ENDPOINT-SCAN-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13aoLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13ar experimental two-sided rod trial. Independent from accepted rods/sliders.
    private static boolean dev44R13arOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ar-BERKSHIRE-CONNECTING-RODS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<Integer> dev44R13arLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13asOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13as-BERKSHIRE-TWO-END-FIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<Integer> dev44R13asLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    // DEV44 R13at: read-only bilateral slider-crank closure comparison.
    private static boolean dev44R13atOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13at-KINEMATICS-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13atLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    // R13au: geometry-gated calibrated linkage. No existing accepted motion is modified.
    private static boolean dev44R13auOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13au-CALIBRATED-LINKAGE-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13auLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<Integer,double[]> dev44R13auCalibrated=new java.util.concurrent.ConcurrentHashMap<>();

    // R13av: bilateral mesh-joint orientation study; never writes an animation pose.
    // Previous R13au used one assumed assignment of the rod ends. This compares
    // both assignments against accepted slider motion and rotating wheel centers.
    private static boolean dev44R13avOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13av-JOINT-ORIENTATION-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13avLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> dev44R13axLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13aw: crosshead pivot neighborhood audit, always read-only.
    private static boolean dev44R13awOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13aw-CROSSHEAD-PIVOT-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13awLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    // R13aq full bilateral candidate sweep is diagnostic-only; no animation writes.
    private static boolean dev44R13aqOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13aq-FULL-BILATERAL-JOINT-SWEEP-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<Integer> dev44R13aqLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13ap: original-mesh joint-neighborhood classification only. No pose writes.
    private static boolean dev44R13apOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ap-BERKSHIRE-LINKAGE-CLASSIFICATION-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13apLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13al never animates unverified connecting-rod chunks.
    private static boolean dev44R13alOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13al-BERKSHIRE-BILATERAL-AUDIT-ON.txt");
              return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13alLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13afOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13af-BERKSHIRE-BOTH-SLIDERS-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13afLogged =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> dev44R13adLogged =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<Integer> dev44R13xLogged =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    // DEV44 R13v opt-in one-shot wheel phase/pivot proof. Probe produces log only.
    private static final java.util.concurrent.atomic.AtomicBoolean dev44R13vProbePending =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    private static boolean dev44R13vProbeOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13v-PROBE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }


    // DEV44 R13az: explicit opt-in only; only the two Berkshire connecting rods.
    private static boolean dev44R13azOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13az-BERKSHIRE-VIRTUAL-RODS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception e) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13azLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // DEV44 R13u: authored TWO-WHEEL rigid rod connections ONLY. OFF by default.
    private static boolean dev44R13uOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13u-TWO-JOINT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception e) { return false; }
    }
    private record Dev44R13uJoint(String mesh,int rod,int wheelA,int wheelB,float[] a,float[] b) {}
    private record Dev44R13uPose(Dev44RigidLinkage.Pose pose,float[] a) {}
    private static volatile java.util.List<Dev44R13uJoint> dev44R13uRows;
    private static java.util.List<Dev44R13uJoint> dev44R13uRows() {
        if(dev44R13uRows!=null) return dev44R13uRows;
        synchronized(LegacyStaticMesh.class) {
            if(dev44R13uRows!=null) return dev44R13uRows;
            java.util.ArrayList<Dev44R13uJoint> out=new java.util.ArrayList<>();
            var file=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13u-VERIFIED-TWO-JOINTS.csv");
            try { if(java.nio.file.Files.isRegularFile(file)) for(String row:java.nio.file.Files.readAllLines(file)) {
                String s=row.trim(); if(s.isEmpty()||s.startsWith("#")||s.startsWith("mesh,")) continue;
                String[] f=s.split(",",-1); if(f.length!=10) { LOGGER.warn("[TC-DEV44-R13u] rejected row: expected 10 columns");continue; }
                String mesh=f[0].trim(); if(!mesh.matches("locomotive_steam_[a-z0-9_]+"))continue;
                int rod=Integer.parseInt(f[1].trim()),wa=Integer.parseInt(f[2].trim()),wb=Integer.parseInt(f[3].trim());
                if(rod<0||wa<0||wb<0||rod==wa||rod==wb||wa==wb)continue;
                float[] a=new float[3],b=new float[3]; boolean finite=true;
                for(int i=0;i<3;i++){a[i]=Float.parseFloat(f[4+i].trim());b[i]=Float.parseFloat(f[7+i].trim());finite &= Float.isFinite(a[i])&&Float.isFinite(b[i]);}
                if(!finite)continue;
                out.add(new Dev44R13uJoint(mesh,rod,wa,wb,a,b));
            }} catch(Exception e){LOGGER.warn("[TC-DEV44-R13u] CSV rejected: {}",e.toString());out.clear();}
            dev44R13uRows=java.util.List.copyOf(out);
            LOGGER.info("[TC-DEV44-R13u] loaded manually verified TWO-JOINT rows={}; empty=motion OFF",out.size());
            return dev44R13uRows;
        }
    }

    // DEV44 R13o: explicitly authored rod/crank pairs only. Never infer mechanical
    // joints from R13n nearest-wheel classifications. OFF without marker and CSV.
    private static boolean dev44R13oOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13o-PAIR-MOTION-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception e) {return false;}
    }
    private record Dev44R13oPair(String mesh,int rod,int crank,int wheel) {}
    private static volatile java.util.List<Dev44R13oPair> dev44R13oPairs;
    private static java.util.List<Dev44R13oPair> dev44R13oPairs() {
        var cached=dev44R13oPairs;
        if(cached!=null)return cached;
        synchronized(LegacyStaticMesh.class) {
            if(dev44R13oPairs!=null)return dev44R13oPairs;
            var rows=new java.util.ArrayList<Dev44R13oPair>();
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13o-Verified-Pairs.csv");
            try { if(java.nio.file.Files.isRegularFile(p)) for(String row:java.nio.file.Files.readAllLines(p)) {
                String t=row.trim();if(t.isEmpty()||t.startsWith("#")||t.startsWith("mesh,"))continue;
                String[] fields=t.split(",",-1);if(fields.length!=4)continue;
                String mesh=fields[0].trim();
                if(!mesh.matches("locomotive_steam_[a-z0-9_]+"))continue;
                int rod=Integer.parseInt(fields[1].trim()),crank=Integer.parseInt(fields[2].trim()),wheel=Integer.parseInt(fields[3].trim());
                if(rod<0||crank<0||wheel<0||rod==crank||rod==wheel||crank==wheel)continue;
                rows.add(new Dev44R13oPair(mesh,rod,crank,wheel));
            }} catch(Exception e){LOGGER.warn("[TC-DEV44-R13o] Invalid pair CSV: {}",e.toString());}
            dev44R13oPairs=java.util.List.copyOf(rows);
            LOGGER.info("[TC-DEV44-R13o] loaded authored rod/crank pairs={}; unlisted chunks untouched",rows.size());
            return dev44R13oPairs;
        }
    }
    // Only identical translation at one authored crank attachment, both solid
    // chunks moved together. This is a limited attachment proof, not the final
    // connecting-rod / crosshead rigid-body solver.
    private static float[] dev44R13oTranslation(
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec w,
            float px,float py,float pz) {
        float a=px-w.centerX(),b=py-w.centerY(),c=pz-w.centerZ();
        float cs=w.cos(),sn=w.sin();
        if(w.axis()==0)return new float[]{0,a*0+(b*(cs-1)-c*sn),b*sn+c*(cs-1)};
        if(w.axis()==1)return new float[]{a*(cs-1)-c*sn,0,a*sn+c*(cs-1)};
        return new float[]{a*(cs-1)-b*sn,a*sn+b*(cs-1),0};
    }

    private static boolean dev44R13nOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13n-JOINT-REVIEW-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF","").trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private boolean dev44R13nReported=false;
    private void dev44R13nReport(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13nOn() || dev44R13nReported || !this.dev44R11Verified) return;
        String mesh=this.source.getPath();
        if (!mesh.startsWith("legacy_meshes/locomotive_steam_")) return;
        dev44R13nReported=true;
        try {
            dev44R13hPrepare();
            var dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13n-Fleet-Joint-Review");
            java.nio.file.Files.createDirectories(dir);
            String name=mesh.substring(mesh.lastIndexOf('/')+1).replace(".tcm","");
            var b=new StringBuilder("mesh,chunk,role,axis,span,endpointA_X,endpointA_Y,endpointA_Z,endpointB_X,endpointB_Y,endpointB_Z,wheelA_X,wheelA_Y,wheelA_Z,wheelB_X,wheelB_Y,wheelB_Z,distanceToCenterA,distanceToCenterB,wheelCenterSeparation,classification,verifiedJoint\n");
            int count=0,review=0,rejected=0; int total=(this.vertexCount+23)/24;
            for(int ch=0;ch<total;ch++) {
                int role=ch<dev44R13hRoles.length?dev44R13hRoles[ch]:0;
                boolean alco=name.equals("locomotive_steam_alco_sc4");
                boolean mapped=dev44R10Mapped(mesh,ch);
                if(role!=1 && role!=2 && role!=3 && !(alco && mapped)) continue;
                int lo=ch*24*FLOATS_PER_VERTEX,hi=Math.min(this.vertexCount*FLOATS_PER_VERTEX,lo+24*FLOATS_PER_VERTEX);
                if(lo>=hi)continue;
                float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
                float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                float[] sum={0,0,0};int n=0;
                for(int j=lo;j+2<hi;j+=FLOATS_PER_VERTEX){
                    for(int ax=0;ax<3;ax++){float v=this.vertices[j+ax];if(!Float.isFinite(v))continue;
                        min[ax]=Math.min(min[ax],v);max[ax]=Math.max(max[ax],v);sum[ax]+=v;}
                    n++;
                }
                if(n==0)continue;
                float[] center={sum[0]/n,sum[1]/n,sum[2]/n};
                int axis=0;for(int a=1;a<3;a++)if(max[a]-min[a]>max[axis]-min[axis])axis=a;
                float[] ea=center.clone(),eb=center.clone();ea[axis]=min[axis];eb[axis]=max[axis];
                var wa=dev44R13mNearest(wheels,ea);
                var wb=dev44R13mNearest(wheels,eb);
                double span=max[axis]-min[axis];
                double da=wa==null?Double.NaN:dev44R13mDist(ea,wa);
                double db=wb==null?Double.NaN:dev44R13mDist(eb,wb);
                double sep=wa==null||wb==null?Double.NaN:Math.sqrt(
                    Math.pow(wa.centerX()-wb.centerX(),2)+Math.pow(wa.centerY()-wb.centerY(),2)+Math.pow(wa.centerZ()-wb.centerZ(),2));
                String verdict;
                if(role==2)verdict="FIXED_SHELL_EXCLUDED";
                else if(wa==null||wb==null)verdict="NO_WHEEL_CENTER";
                else if(span<0.02)verdict="DEGENERATE_GEOMETRY";
                else if(sep<0.02)verdict="BOTH_ENDS_NEAREST_SAME_WHEEL";
                else if(da>1.5||db>1.5)verdict="FAR_FROM_WHEEL_CENTERS";
                else verdict="REVIEW_CANDIDATE_NOT_CRANK_PIN";
                if(verdict.equals("REVIEW_CANDIDATE_NOT_CRANK_PIN"))review++;else rejected++;
                b.append(name).append(',').append(ch).append(',').append(role).append(',').append(axis).append(',').append(span);
                for(float v:ea)b.append(',').append(v);for(float v:eb)b.append(',').append(v);
                for(var w:new traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec[]{wa,wb}){
                    if(w==null)b.append(",,,");else b.append(',').append(w.centerX()).append(',').append(w.centerY()).append(',').append(w.centerZ());
                }
                b.append(',').append(da).append(',').append(db).append(',').append(sep).append(',').append(verdict).append(",false\n");count++;
            }
            java.nio.file.Files.writeString(dir.resolve(name+".csv"),b.toString(),java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[TC-DEV44-R13n-JOINTS] mesh={} candidates={} review={} excluded={} wheels={} verified=0 motionChanges=false",name,count,review,rejected,wheels.size());
        } catch(Exception ex){LOGGER.warn("[TC-DEV44-R13n-JOINTS] failed mesh={} error={}",mesh,ex.toString());}
    }

    private boolean dev44R13kReported = false;
    // Authored centers, computed once (no per-frame vertex centroid scans).
    private float[] dev44R13kCx, dev44R13kCy, dev44R13kCz;
    private int[] dev44R13kNearestCrank;
    private void dev44R13kPrepare() {
        if (dev44R13kCx != null) return;
        int n=(this.vertexCount+23)/24;
        float[] xs=new float[n], ys=new float[n], zs=new float[n];
        for (int ch=0;ch<n;ch++) {
            if (!DEV44_R13_ROD_4.get(ch) && !DEV44_R13_CRANK_4.get(ch)) continue;
            int from=ch*24*FLOATS_PER_VERTEX, to=Math.min(this.vertices.length,from+24*FLOATS_PER_VERTEX);
            int count=0;
            for (int j=from;j<to;j+=FLOATS_PER_VERTEX) {xs[ch]+=vertices[j];ys[ch]+=vertices[j+1];zs[ch]+=vertices[j+2];count++;}
            if(count>0){xs[ch]/=count;ys[ch]/=count;zs[ch]/=count;}
        }
        int[] near=new int[n];java.util.Arrays.fill(near,-1);
        for(int ch=DEV44_R13_ROD_4.nextSetBit(0);ch>=0;ch=DEV44_R13_ROD_4.nextSetBit(ch+1)){
            if(ch>=n)continue;
            double best=Double.POSITIVE_INFINITY;
            for(int k=DEV44_R13_CRANK_4.nextSetBit(0);k>=0;k=DEV44_R13_CRANK_4.nextSetBit(k+1)){
                if(k>=n)continue;
                float dx=xs[ch]-xs[k],dy=ys[ch]-ys[k],dz=zs[ch]-zs[k];
                double distance=dx*dx+dy*dy+dz*dz;
                if(distance<best){best=distance;near[ch]=k;}
            }
        }
        dev44R13kNearestCrank=near;
        dev44R13kCy=ys;dev44R13kCz=zs;dev44R13kCx=xs;
    }
    private boolean dev44R13jReported = false;
    // DEV44 R13h: immutable mesh-space chunk metadata, cached once per mesh instance.
    // Does not cache dynamic wheel phase, wheel transformations or movement data.
    private int[] dev44R13hRoles;
    private float[] dev44R13hCx, dev44R13hCy, dev44R13hCz;
    private void dev44R13hPrepare() {
        if (dev44R13hRoles != null) return;
        int n = (this.vertexCount + 23) / 24;
        int[] roles = new int[n];
        float[] cx = new float[n], cy = new float[n], cz = new float[n];
        String path = this.source.getPath();
        for (int ch = 0; ch < n; ch++) {
            roles[ch] = dev44R13Role(path, ch);
            if (roles[ch] == 0 || roles[ch] == 2) continue;
            int start = ch * 24 * FLOATS_PER_VERTEX;
            int end = Math.min(this.vertices.length, start + 24 * FLOATS_PER_VERTEX);
            int count = 0;
            for (int j = start; j < end; j += FLOATS_PER_VERTEX) {
                cx[ch] += this.vertices[j];
                cy[ch] += this.vertices[j+1];
                cz[ch] += this.vertices[j+2];
                count++;
            }
            if (count > 0) { cx[ch] /= count; cy[ch] /= count; cz[ch] /= count; }
        }
        dev44R13hCx = cx; dev44R13hCy = cy; dev44R13hCz = cz;
        dev44R13hRoles = roles; // publish after all arrays are populated
    }
    // DEV44 R13g: frame-cost sampler; independent of the wheel animator and motion solver.
    // Sample 120 render invocations per mesh instance, then write ONE aggregated row.
    private long dev44R13gCount, dev44R13gTotalNs, dev44R13gMaxNs;
    private static boolean dev44R13gProfileOn() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13G-PROFILE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private void dev44R13gRecord(long started, boolean preview, boolean audit) {
        if (started == 0L) return;
        long delta = System.nanoTime() - started;
        if (delta < 0L) return;
        dev44R13gCount++;
        dev44R13gTotalNs += delta;
        if (delta > dev44R13gMaxNs) dev44R13gMaxNs = delta;
        if (dev44R13gCount < 120L) return;
        String name = this.source.getPath().substring(this.source.getPath().lastIndexOf('/')+1).replace(".tcm", "");
        String mode = audit ? "C_DIAGNOSTIC" : (preview ? "B_LINKAGE" : "A_BASELINE");
        try {
            var folder = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13g-Performance");
            java.nio.file.Files.createDirectories(folder);
            var path = folder.resolve("performance.csv");
            synchronized (DEV44_R13G_WRITE_LOCK) {
                boolean header = !java.nio.file.Files.isRegularFile(path);
                if (header) java.nio.file.Files.writeString(path,
                        "mesh,mode,invocations,meanRenderMs,maxRenderMs,vertexCount\n",
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
                java.nio.file.Files.writeString(path,
                        name+","+mode+","+dev44R13gCount+","+(dev44R13gTotalNs/1000000.0/dev44R13gCount)+","+(dev44R13gMaxNs/1000000.0)+","+this.vertexCount+"\n",
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception e) { LOGGER.warn("[TC-DEV44-R13g] timing report write failed: {}", e.toString()); }
        dev44R13gCount=0L;dev44R13gTotalNs=0L;dev44R13gMaxNs=0L;
    }
    private static final Object DEV44_R13G_WRITE_LOCK = new Object();
    // DEV44 R13f: diagnostic-only fleet audit. Piston-labelled chunks are fixed shells.
    private static final java.util.Set<String> DEV44_R13F_HEADERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long dev44R13fFrames;
    private static boolean dev44R13fOn() {
        try {
            var marker = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13F-FULL-FLEET-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(marker)
                    && java.nio.file.Files.readString(marker, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private static synchronized void dev44R13fLine(String name, String line) {
        try {
            var root = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV44-R13f-Full-Fleet-Audit");
            java.nio.file.Files.createDirectories(root);
            java.nio.file.Files.writeString(root.resolve(name), line + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            LOGGER.warn("[TC-DEV44-R13f] report write failed: {}", ex.toString());
        }
    }
    private void dev44R13fCapture(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13fOn() || !this.dev44R11Verified) return;
        String mesh = this.source.getPath().substring(this.source.getPath().lastIndexOf('/')+1).replace(".tcm", "");
        long frame = ++dev44R13fFrames;
        boolean first = DEV44_R13F_HEADERS.add(mesh);
        if (first) {
            dev44R13fLine("fleet-summary.csv", "MESH,"+mesh+",WHEELS,"+wheels.size()+",STATUS,OBSERVED_NOT_VISUALLY_ACCEPTED");
            dev44R13fLine(mesh+".csv", "type,frame,chunk,role,centerX,centerY,centerZ,nearestWheelX,nearestWheelY,nearestWheelZ,axis,phaseCos,phaseSin,centerDistance,classification");
            LOGGER.info("[TC-DEV44-R13f-FLEET] observed={} wheels={} pistonShellsAreStationary=true", mesh, wheels.size());
        }
        if (!first && frame % 40 != 0) return;
        int total = (this.vertexCount+23)/24;
        int shellCount=0,rodCount=0,crankCount=0;
        for(int ch=0;ch<total;ch++) {
            int role=dev44R13Role(this.source.getPath(),ch);
            if(role==0) continue;
            if(role==1)rodCount++; else if(role==2)shellCount++; else if(role==3)crankCount++;
            int start=ch*24*FLOATS_PER_VERTEX;
            int end=Math.min(this.vertices.length,start+24*FLOATS_PER_VERTEX);
            float x=0,y=0,z=0;int count=0;
            for(int i=start;i<end;i+=FLOATS_PER_VERTEX){x+=this.vertices[i];y+=this.vertices[i+1];z+=this.vertices[i+2];count++;}
            if(count==0)continue;
            x/=count;y/=count;z/=count;
            var best = wheels.isEmpty()?null:dev44R13NearestWheel(wheels,x,y,z,wheels.get(0));
            String nearest = best==null ? ",,,,,,," : ","+best.centerX()+","+best.centerY()+","+best.centerZ()+","+best.axis()+","+best.cos()+","+best.sin()+","+Math.sqrt(Math.pow(x-best.centerX(),2)+Math.pow(y-best.centerY(),2)+Math.pow(z-best.centerZ(),2));
            String type=role==2 ? "FIXED_SHELL" : (role==1 ? "ROD_CANDIDATE" : "CRANK_CANDIDATE");
            String note=role==2 ? "NO_LOCAL_ANIMATION_EXPECTED" : (best==null ? "NO_ROTATING_WHEEL_SPEC" : "UNVERIFIED_ATTACHMENT_CANDIDATE");
            dev44R13fLine(mesh+".csv",type+","+frame+","+ch+","+role+","+x+","+y+","+z+nearest+","+note);
        }
        if(first) dev44R13fLine("fleet-summary.csv", "PARTS,"+mesh+",RODS,"+rodCount+",SHELLS,"+shellCount+",CRANKS,"+crankCount);
        if(first || frame%40==0) for(int i=0;i<wheels.size();i++){
            var w=wheels.get(i);
            dev44R13fLine(mesh+".csv","WHEEL_PHASE,"+frame+","+i+",0,,,,"+w.centerX()+","+w.centerY()+","+w.centerZ()+","+w.axis()+","+w.cos()+","+w.sin()+",,ACCEPTED_WHEEL_PHASE_READ_ONLY");
        }
    }
    private int dev44R13AnchorChunk = -2;
    private int dev44R13LastLoggedState = -1;
    private int dev44R13FindWheelAnchor(String meshKey) {
        if (dev44R13AnchorChunk != -2) return dev44R13AnchorChunk;
        // Cache the wheel chunk ID, not its pose, so phase always follows the
        // accepted existing wheel animator on every rendered frame.
        for (int chunk = 0; chunk < (this.vertexCount+23)/24; chunk++) {
            var spec = traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(meshKey, chunk, this.vertices, FLOATS_PER_VERTEX);
            if (spec != null && spec.rotates() && !dev44R10Mapped(this.source.getPath(), chunk)) {
                dev44R13AnchorChunk = chunk;
                break;
            }
        }
        // No wheel context in inventory/early render: retry next render.
        return dev44R13AnchorChunk == -2 ? -1 : dev44R13AnchorChunk;
    }
    // DEV44 R13b preview: wheel center is fixed; eccentric crank location orbits it.
    // Side-specific reference is inferred from identified crank geometry where available.
    // Rods and crossheads move as groups and never acquire a second clock.
    private static traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec
    dev44R13NearestWheel(java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels,
                        float x,float y,float z,
                        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec fallback) {
        var best=fallback;
        double distance=Double.POSITIVE_INFINITY;
        for (var wheel:wheels) {
            if (wheel.axis()!=fallback.axis()) continue;
            // Penalize wrong side heavily, and use squared physical distance within a side.
            double dx=x-wheel.centerX(),dy=y-wheel.centerY(),dz=z-wheel.centerZ();
            double axial=wheel.axis()==0?dx:(wheel.axis()==1?dy:dz);
            double plane=dx*dx+dy*dy+dz*dz-axial*axial;
            double d=plane+axial*axial*16.0;
            if(d<distance) {distance=d;best=wheel;}
        }
        return best;
    }

    private static float[] dev44R13Motion(
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec wheel,
            int role, float partX, float partY, float partZ,
            float crankX, float crankY, float crankZ, boolean crankFound) {
        float c=wheel.cos(), s=wheel.sin();
        float x=crankFound ? crankX : partX;
        float y=crankFound ? crankY : partY;
        float z=crankFound ? crankZ : partZ;
        // Measured eccentric relative to the accepted wheel center; bounded against
        // malformed labels that select cab/body geometry instead of an actual pin.
        float u=wheel.axis()==0 ? y-wheel.centerY() : x-wheel.centerX();
        float v=wheel.axis()==2 ? y-wheel.centerY() : z-wheel.centerZ();
        float length=(float)Math.hypot(u,v);
        if (!Float.isFinite(length) || length < 0.001f) {u=0.06f;v=0.0f;length=0.06f;}
        float radius=Math.min(0.16f,Math.max(0.02f,length));
        u*=radius/length;v*=radius/length;
        // Exact eccentric translation in the wheel's own rotation plane.
        float du=u*(c-1.0f)-v*s;
        float dv=u*s+v*(c-1.0f);
        float advance=du;
        float rise=dv;
        if (role==2) {
            // Crosshead constrained to its guide: slide in locomotive longitudinal axis.
            // Approximate slider-crank closure with a conservative rod length.
            float rodLength=Math.max(0.4f,radius*3.0f);
            float initial=(float)Math.sqrt(Math.max(0.0f,rodLength*rodLength-v*v));
            float movedV=u*s+v*c;
            float movedU=u*c-v*s;
            float current=(float)Math.sqrt(Math.max(0.0f,rodLength*rodLength-movedV*movedV));
            advance=(movedU+current)-(u+initial);
            rise=0;
        } else if (role==1) {
            // Conservative rigid rod approximation: fixed-end / crank-end midpoint.
            // Exact rod-end orientation is not provable from the chunk labels alone.
            advance*=0.5f;rise*=0.5f;
        }
        if (wheel.axis()==0) return new float[]{0,advance,rise};
        if (wheel.axis()==1) return new float[]{advance,0,rise};
        return new float[]{advance,rise,0};
    }

    private static boolean dev44R10Mapped(String source, int chunk) {
        String meshName = source.replace("legacy_meshes/", "").replace(".tcm", "");
        if (meshName.equals("locomotive_steam_262t")) return DEV44_R10_IDS_0.get(chunk);
        if (meshName.equals("locomotive_steam_4_4_0")) return DEV44_R10_IDS_1.get(chunk);
        if (meshName.equals("locomotive_steam_a4_mallard")) return DEV44_R10_IDS_2.get(chunk);
        if (meshName.equals("locomotive_steam_adler")) return DEV44_R10_IDS_3.get(chunk);
        if (meshName.equals("locomotive_steam_alco_sc4")) return DEV44_R10_IDS_4.get(chunk);
        if (meshName.equals("locomotive_steam_alice_0_4_0")) return DEV44_R10_IDS_5.get(chunk);
        if (meshName.equals("locomotive_steam_berkshire_1225")) return DEV44_R10_IDS_6.get(chunk);
        if (meshName.equals("locomotive_steam_berkshire_765")) return DEV44_R10_IDS_7.get(chunk);
        if (meshName.equals("locomotive_steam_br80")) return DEV44_R10_IDS_8.get(chunk);
        if (meshName.equals("locomotive_steam_br_black_5")) return DEV44_R10_IDS_9.get(chunk);
        if (meshName.equals("locomotive_steam_br_britannia")) return DEV44_R10_IDS_10.get(chunk);
        if (meshName.equals("locomotive_steam_c11")) return DEV44_R10_IDS_11.get(chunk);
        if (meshName.equals("locomotive_steam_c41")) return DEV44_R10_IDS_12.get(chunk);
        if (meshName.equals("locomotive_steam_c41_080")) return DEV44_R10_IDS_13.get(chunk);
        if (meshName.equals("locomotive_steam_c41t")) return DEV44_R10_IDS_14.get(chunk);
        if (meshName.equals("locomotive_steam_climax")) return DEV44_R10_IDS_15.get(chunk);
        if (meshName.equals("locomotive_steam_climax_2")) return DEV44_R10_IDS_16.get(chunk);
        if (meshName.equals("locomotive_steam_coronation_class")) return DEV44_R10_IDS_17.get(chunk);
        if (meshName.equals("locomotive_steam_fowler")) return DEV44_R10_IDS_18.get(chunk);
        if (meshName.equals("locomotive_steam_gs4")) return DEV44_R10_IDS_19.get(chunk);
        if (meshName.equals("locomotive_steam_gwr_101_class")) return DEV44_R10_IDS_20.get(chunk);
        if (meshName.equals("locomotive_steam_gwr_42xx")) return DEV44_R10_IDS_21.get(chunk);
        if (meshName.equals("locomotive_steam_gwr_72xx")) return DEV44_R10_IDS_22.get(chunk);
        if (meshName.equals("locomotive_steam_hall_class")) return DEV44_R10_IDS_23.get(chunk);
        if (meshName.equals("locomotive_steam_j50")) return DEV44_R10_IDS_24.get(chunk);
        if (meshName.equals("locomotive_steam_king_class")) return DEV44_R10_IDS_25.get(chunk);
        if (meshName.equals("locomotive_steam_lssp7")) return DEV44_R10_IDS_26.get(chunk);
        if (meshName.equals("locomotive_steam_midland_compound")) return DEV44_R10_IDS_27.get(chunk);
        if (meshName.equals("locomotive_steam_milw_class_a")) return DEV44_R10_IDS_28.get(chunk);
        if (meshName.equals("locomotive_steam_pannier")) return DEV44_R10_IDS_29.get(chunk);
        if (meshName.equals("locomotive_steam_pe")) return DEV44_R10_IDS_30.get(chunk);
        if (meshName.equals("locomotive_steam_rw_type_2")) return DEV44_R10_IDS_31.get(chunk);
        if (meshName.equals("locomotive_steam_rw_type_3")) return DEV44_R10_IDS_32.get(chunk);
        if (meshName.equals("locomotive_steam_s100_uk")) return DEV44_R10_IDS_33.get(chunk);
        if (meshName.equals("locomotive_steam_s100_us")) return DEV44_R10_IDS_34.get(chunk);
        if (meshName.equals("locomotive_steam_sentinel_y3")) return DEV44_R10_IDS_35.get(chunk);
        if (meshName.equals("locomotive_steam_shay_3_truck")) return DEV44_R10_IDS_36.get(chunk);
        if (meshName.equals("locomotive_steam_skookum")) return DEV44_R10_IDS_37.get(chunk);
        if (meshName.equals("locomotive_steam_southern1102")) return DEV44_R10_IDS_38.get(chunk);
        if (meshName.equals("locomotive_steam_u57")) return DEV44_R10_IDS_39.get(chunk);
        if (meshName.equals("locomotive_steam_vb_0_4_0")) return DEV44_R10_IDS_40.get(chunk);
        if (meshName.equals("locomotive_steam_vb_shay")) return DEV44_R10_IDS_41.get(chunk);
        if (meshName.equals("locomotive_steam_vb_shay_2")) return DEV44_R10_IDS_42.get(chunk);
        if (meshName.equals("locomotive_steam_wwcp_062t")) return DEV44_R10_IDS_43.get(chunk);
        return false;
    }
    // R11: source asset identity guard, based on the exact files used in the offline inspector.
    // This does not certify the user's semantic rod/piston labels.
    private static String dev44R11ExpectedHash(String name) {
        if (name.equals("locomotive_steam_262t")) return "9DC0F4D11D58CD187BE62890C7E5B6565A157152D363D3441E4C3D307C60FB47";
        if (name.equals("locomotive_steam_4_4_0")) return "2ED8C998E1659A31D78EF855046161FB95613D5A5B370781EF783673AB063A2D";
        if (name.equals("locomotive_steam_a4_mallard")) return "172BA1D0F7180892D39733AB15CF539D7AFF1C586B6ABFBEDA52C1970FF84175";
        if (name.equals("locomotive_steam_adler")) return "3B3ABBB3F2D9AD777977AB703D504166D521533A00B06CAC5DD8AF774CECCC32";
        if (name.equals("locomotive_steam_alco_sc4")) return "EA6EF46A62DF7B89CFE8B1AC31C872ED14EEB12253EB91CB99FF3D59D1BAD24E";
        if (name.equals("locomotive_steam_alice_0_4_0")) return "DB87B7F1C6177269203B8D2025C7CBBD34FF6C0F441A1674731F11ECAD263771";
        if (name.equals("locomotive_steam_berkshire_1225")) return "9AD06147C0290FF22C33A568DDF664CCA3BAB00704824BB390DC960E4B818865";
        if (name.equals("locomotive_steam_berkshire_765")) return "8A5588375BC8D284B29DCBA73984BF4EA127232209DE03FDB6AA744B7828A6C4";
        if (name.equals("locomotive_steam_br80")) return "5396F886EA7AE2B05AFB6254F0320906EC0D660B12B025206F1E45764A0000A9";
        if (name.equals("locomotive_steam_br_black_5")) return "88B98EC7C86698DEE9614886515F04AA1FA9373EB83118DF4231FBA8C98AA328";
        if (name.equals("locomotive_steam_br_britannia")) return "163A759BDE575B7D4C6C170353905C8CB8F83788CFC56435247420B3494A0ED0";
        if (name.equals("locomotive_steam_c11")) return "62D540AD3AD66D4EC7056A125660BDCC20BB3DEBBC8081D640C5E4A7B90B4959";
        if (name.equals("locomotive_steam_c41")) return "37188413D060738EBDABB9ABF802B9643820D12EF399194E3FB0A94BA141B0C0";
        if (name.equals("locomotive_steam_c41_080")) return "93CCAE40DDADB884073D88A6CF79B53F6D2617B98D5A8737505AF9F281D06941";
        if (name.equals("locomotive_steam_c41t")) return "AE488515E44DDA3FB2BDF629E0252FEECB14D563C50A7898370993A9C0D5ACE1";
        if (name.equals("locomotive_steam_climax")) return "121896AF8C0E820E9BBF1F78A0CD22307604A7E3A1050D96FE92DC140D4B59ED";
        if (name.equals("locomotive_steam_climax_2")) return "3298F9A26416ABA3277BEAE6E6848D14513F603EA6B6B5B5EEF4B1B25B9C5A3F";
        if (name.equals("locomotive_steam_coronation_class")) return "3108D41AA92ABCE5E0D6319AFD593C42813FB00D2F7FA352782256CCD7BB3AD8";
        if (name.equals("locomotive_steam_fowler")) return "CEDCEFF96571A828E4B9C69D375AD69F63D105332BF48F7F4B3B11EE875F9A28";
        if (name.equals("locomotive_steam_gs4")) return "4B0CCC2822BEF70FC3664F07C7091FF6193741BC59F57B7F6DBB892FE93006C3";
        if (name.equals("locomotive_steam_gwr_101_class")) return "6C021E73387D5AE79A885F9CA5DDD5654FAFCFA16DAE52FC2B64F527059B5795";
        if (name.equals("locomotive_steam_gwr_42xx")) return "EEFE1FAE505DD708CFA8B47E391F831625EBEBA44858FF45055EFE215CDE90D4";
        if (name.equals("locomotive_steam_gwr_72xx")) return "6EF457F7B46CC0018B561904E3C984852712050C67AD05618EB34E9803C3CCC4";
        if (name.equals("locomotive_steam_hall_class")) return "713AD8BFF748E52573EB25B02A320F66766D4F1358ACD60B41A01FB6C9B48E06";
        if (name.equals("locomotive_steam_j50")) return "C0A8A051A1DCCC9982B7B292773CDA8CE1357076498AA6741302FE706DA574CE";
        if (name.equals("locomotive_steam_king_class")) return "69DA8E5507D314E6DDEAEA25C58DB157AD7C8CE8E3F68D256039C127B03324B9";
        if (name.equals("locomotive_steam_lssp7")) return "90D29EBF1278BB7EB3CC21D20C631A89056ED4A3503DD51BC3365ACFDF48A401";
        if (name.equals("locomotive_steam_midland_compound")) return "56A825A205C77326E52E5CF08755FCD9417244955D3281E2A54A75EDE2D20DBC";
        if (name.equals("locomotive_steam_milw_class_a")) return "CBA9DD89C1D287367E9762525A4C16393C10C81F9D95F304C807038539B32961";
        if (name.equals("locomotive_steam_pannier")) return "CB473F135E1B1BEC08EC620222382EC31B65420E4F01599D5E1DBC7280997EBC";
        if (name.equals("locomotive_steam_pe")) return "CC3D8BF40A34E695B06CC617EEEEE39FB1B76AAA510CEFAF16BED62B30E3B4B5";
        if (name.equals("locomotive_steam_rw_type_2")) return "8201E113277CC21ADC203976C04C792DBD090A19C67C5012DF57DC8F9BFE85B6";
        if (name.equals("locomotive_steam_rw_type_3")) return "E1AC625F31296CAA1EBFC7AABDE6001A18F437411795EF6CB8BC29FDC8F58A96";
        if (name.equals("locomotive_steam_s100_uk")) return "0361CEE0F5BBA4DA0EA541CACD9EE0C4615CF88ECDAE604AD9EA003687352FAE";
        if (name.equals("locomotive_steam_s100_us")) return "06BFAD8CDA5DE0472DD77B532353BC9D98FEC40DFA62637F6F56BC8D311B88C7";
        if (name.equals("locomotive_steam_sentinel_y3")) return "F2557379BD6465FB0C7A9ED71EDFB8DEF45A8CCD7FDC3F4106D98C8C0680CECA";
        if (name.equals("locomotive_steam_shay_3_truck")) return "37B23DEC217825D10F5C8E1B368503AD858F8FE7B1A4BC8B10A6D09612D3F71E";
        if (name.equals("locomotive_steam_skookum")) return "999CECAAA803E4F8CF147544B148A2405C2457B9147294051DF0859314445F0D";
        if (name.equals("locomotive_steam_southern1102")) return "CCD566FBE11B01C2F5784653A0CF405B9A7A7336F297C994C8302FC6A7259DFE";
        if (name.equals("locomotive_steam_u57")) return "9553FE1E20C6A2965A8251EFCD75A771E897AE1708BE1922A00085AB5B2F091A";
        if (name.equals("locomotive_steam_vb_0_4_0")) return "8094F590A8D49664A51272E90D0D8664CF37881C82383DE31F6D62C2A8AAAD40";
        if (name.equals("locomotive_steam_vb_shay")) return "1FC1209BC0288FB61671645FF3D7C2693E1C1A42115E63CF3C6DB0E8DA93A179";
        if (name.equals("locomotive_steam_vb_shay_2")) return "E68F978CA29FA3AA46ECBB6E65BF0367A17602A9846881A5AE8635D706656B50";
        if (name.equals("locomotive_steam_wwcp_062t")) return "04CE9253AED0850AF62E5A4895EE73ABB70EE066229B71BA251612DB5D1773CF";
        return null;
    }
    private final boolean dev44R11Verified;
    // R12a: once per state per loaded mesh; no per-frame log flood.
    private transient int dev44R12LastReportedMode = -1;
    private static String dev44R11Sha256(byte[] raw) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x54434D31; // TCM1
    private static final int VERSION = 1;
    private static final int FLOATS_PER_VERTEX = 8;

    // DEV44 R9: client-only, opt-in triangle ray picker. Does not modify source assets.
    private static volatile boolean requestPick;
    private static volatile boolean lastP, lastH, lastJ;
    private static volatile String selectedMesh = "";
    private static volatile int selectedChunk = -1;
    private static volatile boolean hideSelected;
    private static double nearestHit = 12.0;
    private static String hitMesh = "";
    private static int hitChunk = -1;
    private static final Path PICK_LOG = Path.of("DEV44-R9-PICKED-PARTS.csv");
    private static final float[][] corners = new float[4][3];
    static {
        MinecraftForge.EVENT_BUS.register(new PickerEvents());
        LOGGER.info("[TC-DEV44-R9] Crosshair picker registered: P=pick H=hide/unhide J=clear");
    }
    private static final class PickerEvents {
        @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.screen != null) return;
            long window = mc.getWindow().getWindow();
            boolean p = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_P);
            boolean h = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_H);
            boolean j = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_J);
            if (p && !lastP) { requestPick = true; nearestHit = 12.0; hitMesh=""; hitChunk=-1; }
            if (h && !lastH && selectedChunk >= 0) {
                hideSelected = !hideSelected;
                LOGGER.info("[TC-DEV44-R9] {} mesh={} chunk={}", hideSelected?"TEMP HIDE":"SHOW",selectedMesh,selectedChunk);
            }
            if (j && !lastJ) { selectedMesh=""; selectedChunk=-1; hideSelected=false; LOGGER.info("[TC-DEV44-R9] Selection cleared"); }
            lastP=p; lastH=h; lastJ=j;
        }
        @SubscribeEvent public void afterEntities(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !requestPick) return;
            requestPick = false;
            if (hitChunk >= 0) {
                selectedMesh=hitMesh; selectedChunk=hitChunk; hideSelected=false;
                LOGGER.info("[TC-DEV44-R9] PICKED mesh={} chunk={} range={}", selectedMesh,selectedChunk,nearestHit);
                Minecraft mc=Minecraft.getInstance();
                if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "DEV44 picked: " + selectedMesh + " #" + selectedChunk + " (H hide, J clear)"),true);
                try {
                    if (!Files.exists(PICK_LOG)) Files.writeString(PICK_LOG,"mesh,chunk,range\n",StandardOpenOption.CREATE);
                    Files.writeString(PICK_LOG,selectedMesh+","+selectedChunk+","+nearestHit+"\n",StandardOpenOption.APPEND);
                } catch (IOException ex) { LOGGER.warn("[TC-DEV44-R9] Cannot write selection CSV",ex); }
            } else {
                LOGGER.info("[TC-DEV44-R9] No visible legacy mesh triangle under crosshair (12-block reach)");
                Minecraft mc=Minecraft.getInstance();
                if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("DEV44: no mesh triangle hit"),true);
            }
        }
    }
    private static double hitTriangle(float[] a, float[] b, float[] c, Vector3f direction) {
        // Double-sided Moller-Trumbore ray intersection; ray origin is the camera.
        double e1x=b[0]-a[0],e1y=b[1]-a[1],e1z=b[2]-a[2];
        double e2x=c[0]-a[0],e2y=c[1]-a[1],e2z=c[2]-a[2];
        double px=direction.y()*e2z-direction.z()*e2y;
        double py=direction.z()*e2x-direction.x()*e2z;
        double pz=direction.x()*e2y-direction.y()*e2x;
        double det=e1x*px+e1y*py+e1z*pz;
        if (Math.abs(det)<1.0e-9) return Double.POSITIVE_INFINITY;
        double inv=1.0/det;
        double tx=-a[0],ty=-a[1],tz=-a[2];
        double u=(tx*px+ty*py+tz*pz)*inv;
        if (u<0 || u>1) return Double.POSITIVE_INFINITY;
        double qx=ty*e1z-tz*e1y,qy=tz*e1x-tx*e1z,qz=tx*e1y-ty*e1x;
        double v=(direction.x()*qx+direction.y()*qy+direction.z()*qz)*inv;
        if (v<0 || u+v>1) return Double.POSITIVE_INFINITY;
        double t=(e2x*qx+e2y*qy+e2z*qz)*inv;
        return t>0.001 ? t : Double.POSITIVE_INFINITY;
    }
    private final ResourceLocation source;
    private final float[] vertices;
    private final int vertexCount;
    private final float minX, minY, minZ, maxX, maxY, maxZ;
    private final int stabilizedZeroDimensions;

    private LegacyStaticMesh(ResourceLocation source, float[] vertices, int vertexCount,
                             float minX, float minY, float minZ,
                             float maxX, float maxY, float maxZ,
                             int stabilizedZeroDimensions, boolean r11Verified) {
        this.dev44R11Verified = r11Verified;
        this.source = source;
        this.vertices = vertices;
        this.vertexCount = vertexCount;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.stabilizedZeroDimensions = stabilizedZeroDimensions;
    }

    public static LegacyStaticMesh load(ResourceLocation location) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(location)
                    .orElseThrow(() -> new IOException("Missing legacy mesh: " + location));
            try (InputStream raw = resource.open()) {
                byte[] rawBytes = raw.readAllBytes();
                String meshName = location.getPath().replace("legacy_meshes/", "").replace(".tcm", "");
                String expectedHash = dev44R11ExpectedHash(meshName);
                String runtimeHash = expectedHash == null ? null : dev44R11Sha256(rawBytes);
                boolean verified = expectedHash != null && expectedHash.equals(runtimeHash);
                if (expectedHash != null) LOGGER.info("[TC-DEV44-R12a-LOAD] mesh={} shaMatch={} actual={} expected={} (render hide state reported separately)", meshName, verified, runtimeHash, expectedHash);
                DataInputStream in = new DataInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(rawBytes)));
                int magic = in.readInt();
                int version = in.readInt();
                if (magic != MAGIC || version != VERSION) {
                    throw new IOException("Unsupported legacy mesh header for " + location);
                }
                int vertexCount = in.readInt();
                if (vertexCount <= 0 || vertexCount > 2_000_000) {
                    throw new IOException("Invalid legacy mesh vertex count " + vertexCount + " for " + location);
                }
                float minX = in.readFloat(); float minY = in.readFloat(); float minZ = in.readFloat();
                float maxX = in.readFloat(); float maxY = in.readFloat(); float maxZ = in.readFloat();
                int stabilized = in.readInt();
                float[] data = new float[vertexCount * FLOATS_PER_VERTEX];
                for (int i = 0; i < data.length; i++) data[i] = in.readFloat();
                LegacyStaticMesh mesh = new LegacyStaticMesh(location, data, vertexCount,
                        minX, minY, minZ, maxX, maxY, maxZ, stabilized, verified);
                LOGGER.info("[TC-LEGACY-MESH] mesh={} vertices={} quads={} bounds=({},{},{})..({},{},{}) zeroThicknessStabilized={}",
                        location, vertexCount, vertexCount / 4,
                        minX, minY, minZ, maxX, maxY, maxZ, stabilized, verified);
                return mesh;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load Traincraft legacy mesh " + location, ex);
        }
    }

public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        final long dev44R13gStarted = dev44R13gProfileOn() ? System.nanoTime() : 0L;
        PoseStack.Pose pose = poseStack.last();

        int activeChunk = -1;
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec transform = null;
        String meshKey = "legacy:" + this.source;
        boolean pickNow=requestPick;
        Vector3f ray=pickNow ? new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector()) : null;

        final boolean r12Requested = dev44R12HideEnabled();
        final boolean r12Hide = this.dev44R11Verified && r12Requested;
        final int r12Mode = r12Hide ? 2 : (r12Requested ? 1 : 0);
        if (dev44R12LastReportedMode != r12Mode) {
            int suppressed = 0;
            if (r12Hide) {
                int lastChunk = (this.vertexCount - 1) / 24;
                for (int chunk = 0; chunk <= lastChunk; chunk++) {
                    if (dev44R10Mapped(this.source.getPath(), chunk)) suppressed++;
                }
            }
            LOGGER.info("[TC-DEV44-R13] mesh={} marker={} requested={} shaVerified={} hide={} suppressedChunks={} visibleHighlight={} cwd={}",
                    this.source, dev44R12MarkerPath().toAbsolutePath().normalize(), r12Requested,
                    this.dev44R11Verified, r12Hide, suppressed, !r12Hide && this.dev44R11Verified,
                    java.nio.file.Path.of("").toAbsolutePath().normalize());
            dev44R12LastReportedMode = r12Mode;
        }
        final boolean r13jAlco = this.source.getPath().equals("legacy_meshes/locomotive_steam_alco_sc4.tcm")
                && this.dev44R11Verified && dev44R13jAlcoProofOn();
        if (r13jAlco && !dev44R13jReported) {
            LOGGER.info("[TC-DEV44-R13j-ALCO-SC4] mesh={} verified={} hide={} mode=AUTHORED-ROD-VISIBILITY-ONLY nativeWheelAnimation=UNCHANGED",
                    this.source, this.dev44R11Verified, r12Hide);
            dev44R13jReported = true;
        }
        // Explicit ALCO-only motion switch. The R13j proof switch still governs visibility.
        final boolean r13kAlco = r13jAlco && dev44R13kAlcoMotionOn() && !dev44R13lReviewOn() && !dev44R13mCaptureOn() && !dev44R13nOn() && !dev44R13oOn() && !dev44R13uOn();
        if(r13kAlco) {
            dev44R13kPrepare();
            if(!dev44R13kReported) {
                LOGGER.info("[TC-DEV44-R13k-ALCO-SC4] enabled=true mode=ACCEPTED-WHEEL-PHASE-ROD-TRANSLATION-PREVIEW otherMeshes=UNCHANGED fixedShells=UNCHANGED");
                dev44R13kReported=true;
            }
        }
        // DEV44 R13v: Berkshire 765 one-shot ACTUAL wheel pivot / model-chunk probe.
        // Read only; never changes poses, buffers, wheel phases, or animation state.
        if (this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm")
                && dev44R13vProbeOn() && dev44R13vProbePending.compareAndSet(true,false)) {
            try {
                dev44R13hPrepare();
                int max=(this.vertexCount+23)/24;
                LOGGER.info("[TC-DEV44-R13v] BEGIN mesh={} chunks={} wheel=ACCEPTED-ANIMATOR rodCrank=R13p-UNVERIFIED",
                        this.source.getPath(),max);
                for(int wi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    if(wi>=max)continue;
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,wi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null) { LOGGER.info("[TC-DEV44-R13v] WHEEL chunk={} spec=null",wi);continue; }
                    LOGGER.info("[TC-DEV44-R13v] WHEEL chunk={} rotates={} axis={} cx={} cy={} cz={} cos={} sin={}",
                        wi,w.rotates(),w.axis(),w.centerX(),w.centerY(),w.centerZ(),w.cos(),w.sin());
                }
                for(int ci:new int[]{348,359}) {
                    if(ci>=max)continue;
                    LOGGER.info("[TC-DEV44-R13v] PART chunk={} tagged={} role={} cx={} cy={} cz={} NOT_A_VERIFIED_JOINT",
                        ci,dev44R10Mapped(this.source.getPath(),ci),dev44R13hRoles[ci],
                        dev44R13hCx[ci],dev44R13hCy[ci],dev44R13hCz[ci]);
                }
                LOGGER.info("[TC-DEV44-R13v] END newMotion=OFF wheelAnimator=UNCHANGED");
            } catch(Exception ex) { LOGGER.warn("[TC-DEV44-R13v] probe error: {}",ex.toString()); }
        }
        // DEV44 R13o explicit-pair proof. Previous previews must not stack.
        final boolean r13wIsolate = dev44R13wOn()
                && this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm");
        final boolean r13uEnabled=dev44R13uOn() && this.dev44R11Verified && !r13wIsolate;
        final boolean r13oEnabled=dev44R13oOn() && this.dev44R11Verified && !r13uEnabled && !r13wIsolate;
        final java.util.HashMap<Integer,float[]> r13oOffsets=new java.util.HashMap<>();
        if(r13oEnabled) {
            dev44R13hPrepare();
            String r13oMesh=this.source.getPath().replace("legacy_meshes/","").replace(".tcm","");
            for(var pair:dev44R13oPairs()) {
                if(!pair.mesh().equals(r13oMesh))continue;
                int max=(this.vertexCount+23)/24;
                if(pair.rod()>=max||pair.crank()>=max||pair.wheel()>=max)continue;
                if(!dev44R10Mapped(this.source.getPath(),pair.rod()) ||
                   !dev44R10Mapped(this.source.getPath(),pair.crank()))continue;
                // Never move stationary cylinder-shell chunks.
                if(dev44R13hRoles[pair.rod()]==2||dev44R13hRoles[pair.crank()]==2)continue;
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,pair.wheel(),this.vertices,FLOATS_PER_VERTEX);
                if(w==null||!w.rotates())continue;
                float[] d=dev44R13oTranslation(w,dev44R13hCx[pair.crank()],dev44R13hCy[pair.crank()],dev44R13hCz[pair.crank()]);
                // Refuse conflicted chunks: never silently choose an attachment.
                if(r13oOffsets.containsKey(pair.rod())||r13oOffsets.containsKey(pair.crank())){
                    r13oOffsets.remove(pair.rod());r13oOffsets.remove(pair.crank());continue;
                }
                r13oOffsets.put(pair.rod(),d);r13oOffsets.put(pair.crank(),d);
            }
        }
        // DEV44 R13af: opt-in paired candidate piston/crosshead SLIDER movement.
        // Uses each side's actual accepted wheel phase. No rotation and NO fixed-cylinder motion.
        // Only 391 (+Z) and 1011 (-Z) are eligible; all remaining chunks are untouched.
        // Inferred role/slider travel: visual trial, NOT a physically pinned piston solver.
        if (dev44R13afOn() && r13wIsolate) {
            final int[] r13afChunks={391,1011};
            for (int r13afSide=0;r13afSide<2;r13afSide++) {
                final int r13afChunk=r13afChunks[r13afSide];
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13afWheel=null;
                for(int r13afWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var r13afCandidate=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                            meshKey,r13afWi,this.vertices,FLOATS_PER_VERTEX);
                    if(r13afCandidate==null || !r13afCandidate.rotates() || r13afCandidate.axis()!=2)continue;
                    if((r13afSide==0 && r13afCandidate.centerZ()<0.10f)
                            || (r13afSide==1 && r13afCandidate.centerZ()>-0.10f))continue;
                    if(Math.abs(r13afCandidate.centerX()+0.5f)<0.02f
                            && Math.abs(r13afCandidate.centerY()+0.5625f)<0.02f) {
                        r13afWheel=r13afCandidate;break;
                    }
                }
                if(r13afChunk<dev44R13hRoles.length && dev44R10Mapped(this.source.getPath(),r13afChunk)
                        && dev44R13hRoles[r13afChunk]!=2 && !r13oOffsets.containsKey(r13afChunk)
                        && r13afWheel!=null) {
                    // Small strictly axial stroke, centered around original rest pose.
                    final float r13afStroke=0.09375f*r13afWheel.cos();
                    r13oOffsets.put(r13afChunk,new float[]{r13afStroke,0f,0f});
                    int r13afQuad=Math.max(0,Math.min(3,(int)Math.floor(
                            (Math.atan2(r13afWheel.sin(),r13afWheel.cos())+Math.PI)/(Math.PI/2.0))));
                    if(dev44R13afLogged.add(r13afSide*10+r13afQuad))LOGGER.info(
                            "[TC-DEV44-R13af] side={} chunk={} SLIDER_CANDIDATE=APPLIED quadrant={} strokeX={} wheelZ={} fixedShells=UNTOUCHED",
                            r13afSide==0?"POS_Z":"NEG_Z",r13afChunk,r13afQuad,r13afStroke,r13afWheel.centerZ());
                } else if(dev44R13afLogged.add(90+r13afSide)) LOGGER.warn(
                        "[TC-DEV44-R13af] side={} chunk={} REJECTED: wheel/mapping/role/conflict guard",
                        r13afSide==0?"POS_Z":"NEG_Z",r13afChunk);
            }
        }

        // R13u computes each authored rod pose ONCE per render, never per vertex.
        final java.util.HashMap<Integer,Dev44R13uPose> r13uPoses=new java.util.HashMap<>();
        if(r13uEnabled) {
            dev44R13hPrepare();
            String current=this.source.getPath().replace("legacy_meshes/","").replace(".tcm","");
            java.util.HashSet<Integer> rejected=new java.util.HashSet<>();
            for(var j:dev44R13uRows()) {
                if(!j.mesh().equals(current)) continue;
                int max=(this.vertexCount+23)/24;
                if(j.rod()>=max||j.wheelA()>=max||j.wheelB()>=max)continue;
                if(!dev44R10Mapped(this.source.getPath(),j.rod()) || dev44R13hRoles[j.rod()]==2)continue;
                var wa=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,j.wheelA(),this.vertices,FLOATS_PER_VERTEX);
                var wb=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,j.wheelB(),this.vertices,FLOATS_PER_VERTEX);
                if(wa==null||wb==null||!wa.rotates()||!wb.rotates()||wa.axis()!=wb.axis()) continue;
                float[] ca={wa.centerX(),wa.centerY(),wa.centerZ()};
                float[] cb={wb.centerX(),wb.centerY(),wb.centerZ()};
                float[] da=Dev44RigidLinkage.wheelPoint(j.a(),ca,wa.cos(),wa.sin(),wa.axis());
                float[] db=Dev44RigidLinkage.wheelPoint(j.b(),cb,wb.cos(),wb.sin(),wb.axis());
                var p=Dev44RigidLinkage.solve(j.a(),j.b(),da,db,wa.axis(),0.015f);
                if(p==null) continue; // no stretching or forced fit
                if(r13uPoses.containsKey(j.rod())){rejected.add(j.rod());r13uPoses.remove(j.rod());}
                else if(!rejected.contains(j.rod()))r13uPoses.put(j.rod(),new Dev44R13uPose(p,j.a()));
            }
        }

        // DEV44 R13ab: Berkshire 765 rod348 candidate rest-geometry calibration.
        // Actual R13y sample: A eccentricity=(-0.046875,+0.09375), B=(+0.046875,+0.09375).
        // These are not verified crank pins and cannot both be assumed same-phase.
        // Do not hide/reject the whole rod on a misleading two-pin constraint.
        // Rigidly translate the EXISTING mesh by the measured A-wheel eccentric motion.
        // This preserves every vertex and its original length; B is NOT asserted attached.
        final boolean r13zBerkshire=dev44R13xOn() && r13wIsolate;
        if(r13zBerkshire) {
            final float[] a={-0.546875f,-0.46875f,0.34375f};
            final float[] b={ 1.984375f,-0.46875f,0.34375f};
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec wa=null,wb=null;
            // Match observed axle positions instead of relying on guessed chunk IDs.
            for(int wi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                        meshKey,wi,this.vertices,FLOATS_PER_VERTEX);
                if(w==null || !w.rotates() || w.axis()!=2)continue;
                if(Math.abs(w.centerX()+0.5f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f
                        && Math.abs(w.centerZ()-0.31313f)<0.04f)wa=w;
                if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f
                        && Math.abs(w.centerZ()-0.31313f)<0.04f)wb=w;
            }
            if(wa!=null && wb!=null && dev44R10Mapped(this.source.getPath(),348)
                    && dev44R13hRoles[348]!=2) {
                float[] ca={wa.centerX(),wa.centerY(),wa.centerZ()};
                // R13ab: R13aa measured phaseDelta=0 throughout all four quadrants.
                // In source-rest pose, A eccentricX=-0.046875, while B's proposed
                // mesh extremum eccentricX=+0.046875. Reconcile the *rest geometry*
                // of this ONE rod (not the mesh asset or the wheels) by 0.09375 blocks.
                // This is a candidate endpoint calibration, NOT a proved physical pin.
                final float[] bAligned={wb.centerX()+(a[0]-wa.centerX()),
                        wb.centerY()+(a[1]-wa.centerY()),b[2]};
                final float rodRestScaleX=(bAligned[0]-a[0])/(b[0]-a[0]);
                final float[] da=Dev44RigidLinkage.wheelPoint(a,ca,wa.cos(),wa.sin(),2);
                final float[] cb={wb.centerX(),wb.centerY(),wb.centerZ()};
                final float[] db=Dev44RigidLinkage.wheelPoint(bAligned,cb,wb.cos(),wb.sin(),2);
                var r13zLinkPose=Dev44RigidLinkage.solve(a,bAligned,da,db,2,0.0005f);
                double bxOffset=b[0]-wb.centerX(),byOffset=b[1]-wb.centerY();
                double axOffset=a[0]-wa.centerX(),ayOffset=a[1]-wa.centerY();
                double eccentricMismatch=Math.hypot(axOffset-bxOffset,ayOffset-byOffset);
                int quadrant=(int)Math.floor((Math.atan2(wa.sin(),wa.cos())+Math.PI)/(Math.PI/2.0));
                quadrant=Math.max(0,Math.min(3,quadrant));
                // R13aa: strictly observational second crankpin gap measurement.
                // Use actual B wheel phase, never synthesize a matching phase.
                // This does NOT affect R13z's accepted-looking rigid preview pose.
                float[] actualB=Dev44RigidLinkage.wheelPoint(bAligned,cb,wb.cos(),wb.sin(),2);
                double secondPinGap=Math.sqrt(
                        Math.pow(db[0]-actualB[0],2)+Math.pow(db[1]-actualB[1],2)
                        +Math.pow(db[2]-actualB[2],2));
                double phaseA=Math.atan2(wa.sin(),wa.cos());
                double phaseB=Math.atan2(wb.sin(),wb.cos());
                double phaseDelta=Math.atan2(Math.sin(phaseB-phaseA),Math.cos(phaseB-phaseA));
                if(dev44R13xLogged.add(quadrant))LOGGER.info(
                        "[TC-DEV44-R13ab] quadrant={} rod348={} mode=CALIBRATED_REST_GEOMETRY secondPin=CANDIDATE phaseA={} phaseB={} phaseDelta={} secondPinGap={} originalEccentricMismatch={} restScaleX={} lengthResidual={} centerA=({},{},{}) centerB=({},{},{})",
                        quadrant,r13zLinkPose==null?"REJECTED":"POSE_APPLIED",
                        phaseA,phaseB,phaseDelta,secondPinGap,eccentricMismatch,rodRestScaleX,
                        Math.abs(Math.hypot(db[0]-da[0],db[1]-da[1])-Math.hypot(bAligned[0]-a[0],bAligned[1]-a[1])),
                        wa.centerX(),wa.centerY(),wa.centerZ(),wb.centerX(),wb.centerY(),wb.centerZ());
                if(r13zLinkPose!=null)r13uPoses.put(348,new Dev44R13uPose(r13zLinkPose,a));
            } else if(dev44R13xLogged.add(9))LOGGER.warn(
                    "[TC-DEV44-R13ab] Rod348 preview rejected: matching animated wheel or safe mesh role absent. No motion.");
        }

        // DEV44 R13ad: isolated opposite-Z-side geometry trial for candidate rod733.
        // Endpoints are mirrored rest geometry of accepted rod348; physical pins remain UNVERIFIED.
        // This does NOT modify rod348 or the R13ab code path. Default switch OFF.
        if(dev44R13adOn() && r13wIsolate) {
            final float[] r13adA={-0.546875f,-0.46875f,-0.45937505f};
            final float[] r13adB={1.984375f,-0.46875f,-0.45937505f};
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13adWa=null,r13adWb=null;
            // Wheel ownership is established by the animated wheel's actual center on the -Z side.
            for(int r13adWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                var r13adW=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                        meshKey,r13adWi,this.vertices,FLOATS_PER_VERTEX);
                if(r13adW==null || !r13adW.rotates() || r13adW.axis()!=2 || r13adW.centerZ()>-0.10f)continue;
                if(Math.abs(r13adW.centerX()+0.5f)<0.02f && Math.abs(r13adW.centerY()+0.5625f)<0.02f)
                    r13adWa=r13adW;
                if(Math.abs(r13adW.centerX()-1.9375f)<0.02f && Math.abs(r13adW.centerY()+0.5625f)<0.02f)
                    r13adWb=r13adW;
            }
            if(r13adWa!=null && r13adWb!=null && dev44R10Mapped(this.source.getPath(),733)
                    && dev44R13hRoles[733]!=2 && !r13uPoses.containsKey(733)) {
                float[] r13adCa={r13adWa.centerX(),r13adWa.centerY(),r13adWa.centerZ()};
                float[] r13adCb={r13adWb.centerX(),r13adWb.centerY(),r13adWb.centerZ()};
                final float[] r13adBAligned={r13adWb.centerX()+(r13adA[0]-r13adWa.centerX()),
                        r13adWb.centerY()+(r13adA[1]-r13adWa.centerY()),r13adB[2]};
                float[] r13adDa=Dev44RigidLinkage.wheelPoint(r13adA,r13adCa,r13adWa.cos(),r13adWa.sin(),2);
                float[] r13adDb=Dev44RigidLinkage.wheelPoint(r13adBAligned,r13adCb,r13adWb.cos(),r13adWb.sin(),2);
                var r13adLinkPose=Dev44RigidLinkage.solve(r13adA,r13adBAligned,r13adDa,r13adDb,2,0.0005f);
                double r13adPhaseA=Math.atan2(r13adWa.sin(),r13adWa.cos());
                double r13adPhaseB=Math.atan2(r13adWb.sin(),r13adWb.cos());
                double r13adPhaseDelta=Math.atan2(Math.sin(r13adPhaseB-r13adPhaseA),Math.cos(r13adPhaseB-r13adPhaseA));
                double r13adRestScaleX=(r13adBAligned[0]-r13adA[0])/(r13adB[0]-r13adA[0]);
                int r13adQuadrant=(int)Math.floor((r13adPhaseA+Math.PI)/(Math.PI/2.0));
                r13adQuadrant=Math.max(0,Math.min(3,r13adQuadrant));
                if(dev44R13adLogged.add(r13adQuadrant))LOGGER.info(
                        "[TC-DEV44-R13ad] quadrant={} rod733={} oppositeSide=CANDIDATE secondPin=UNVERIFIED phaseDelta={} restScaleX={} lengthResidual={} wheelZ_A={} wheelZ_B={}",
                        r13adQuadrant,r13adLinkPose==null?"REJECTED":"POSE_APPLIED",r13adPhaseDelta,
                        r13adRestScaleX,Math.abs(Math.hypot(r13adDb[0]-r13adDa[0],r13adDb[1]-r13adDa[1])
                                -Math.hypot(r13adBAligned[0]-r13adA[0],r13adBAligned[1]-r13adA[1])),
                        r13adWa.centerZ(),r13adWb.centerZ());
                if(r13adLinkPose!=null)r13uPoses.put(733,new Dev44R13uPose(r13adLinkPose,r13adA));
            } else if(dev44R13adLogged.add(9))LOGGER.warn(
                    "[TC-DEV44-R13ad] rod733 REJECTED: missing opposite-side wheel pair or role guard. No motion.");
        }

        // DEV44 R13al: BILATERAL main-rod joint consistency AUDIT.
        // Chunk 378/746 endpoints are mesh extrema and are NOT verified pin vertices.
        // Read-only by design: a rejected pose must NEVER flicker or distort a component.
        // Existing rod348/733 and slider391/1011 motion above is not modified.
        if (dev44R13alOn() && r13wIsolate) {
            for(int r13alSide=0;r13alSide<2;r13alSide++) {
                final int r13alChunk=r13alSide==0?378:746;
                final int r13alSlider=r13alSide==0?391:1011;
                final float r13alZ=r13alSide==0?0.40625f:-0.5218751f;
                final float[] r13alRestA={0.296875f,-0.53125f,r13alZ};
                final float[] r13alRestB={1.953125f,-0.53125f,r13alZ};
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13alWheel=null;
                for(int r13alWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}){
                    var r13alW=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,r13alWi,this.vertices,FLOATS_PER_VERTEX);
                    if(r13alW==null || !r13alW.rotates() || r13alW.axis()!=2)continue;
                    if(r13alSide==0 && r13alW.centerZ()<0.1f)continue;
                    if(r13alSide==1 && r13alW.centerZ()>-0.1f)continue;
                    if(Math.abs(r13alW.centerX()-1.9375f)<0.02f && Math.abs(r13alW.centerY()+0.5625f)<0.02f){r13alWheel=r13alW;break;}
                }
                if(r13alWheel==null || !dev44R10Mapped(this.source.getPath(),r13alChunk)
                        || !dev44R10Mapped(this.source.getPath(),r13alSlider)
                        || dev44R13hRoles[r13alChunk]==2 || !r13oOffsets.containsKey(r13alSlider)){
                    if(dev44R13alLogged.add(90+r13alSide))LOGGER.warn(
                        "[TC-DEV44-R13al] side={} chunk={} VALIDATION_UNAVAILABLE wheel/mesh/slider guard; no changes",
                        r13alSide==0?"POS_Z":"NEG_Z",r13alChunk);
                    continue;
                }
                float[] r13alPivot={r13alWheel.centerX(),r13alWheel.centerY(),r13alWheel.centerZ()};
                float[] r13alB=Dev44RigidLinkage.wheelPoint(r13alRestB,r13alPivot,r13alWheel.cos(),r13alWheel.sin(),2);
                float[] r13alStroke=r13oOffsets.get(r13alSlider);
                float[] r13alA={r13alRestA[0]+r13alStroke[0],r13alRestA[1],r13alRestA[2]};
                double r13alRestLength=Math.hypot(r13alRestB[0]-r13alRestA[0],r13alRestB[1]-r13alRestA[1]);
                double r13alLiveLength=Math.hypot(r13alB[0]-r13alA[0],r13alB[1]-r13alA[1]);
                double r13alResidual=Math.abs(r13alLiveLength-r13alRestLength);
                int r13alQuad=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(r13alWheel.sin(),r13alWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(dev44R13alLogged.add(r13alSide*10+r13alQuad))LOGGER.info(
                    "[TC-DEV44-R13al] side={} chunk={} slider={} quadrant={} candidateRestLength={} candidateLiveLength={} candidateResidual={} sliderStroke={} status={} movingRod=DISABLED fixedShells=UNTOUCHED",
                    r13alSide==0?"POS_Z":"NEG_Z",r13alChunk,r13alSlider,r13alQuad,
                    r13alRestLength,r13alLiveLength,r13alResidual,r13alStroke[0],
                    r13alResidual<=0.015?"CANDIDATE_POSSIBLE":"REJECTED_GEOMETRY");
            }
        }

        // DEV44 R13am: bilateral constant-length slider-crank SOLUTION AUDIT.
        // Geometry candidates are mesh extrema, NOT verified crankpin vertices.
        // Never alter existing rod348/733, slider391/1011, or static cylinder meshes.
        if (dev44R13amOn() && r13wIsolate) {
            for (int amSide=0;amSide<2;amSide++) {
                final int amRod=amSide==0?378:746, amSlider=amSide==0?391:1011;
                final float amZ=amSide==0?0.40625f:-0.5218751f;
                final float[] amA={0.296875f,-0.53125f,amZ};
                final float[] amB={1.953125f,-0.53125f,amZ};
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec amWheel=null;
                for (int amWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var amW=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,amWi,this.vertices,FLOATS_PER_VERTEX);
                    if(amW==null || !amW.rotates() || amW.axis()!=2)continue;
                    if(amSide==0 && amW.centerZ()<0.1f)continue;
                    if(amSide==1 && amW.centerZ()>-0.1f)continue;
                    if(Math.abs(amW.centerX()-1.9375f)<0.02f && Math.abs(amW.centerY()+0.5625f)<0.02f){amWheel=amW;break;}
                }
                if(amWheel==null || !dev44R10Mapped(this.source.getPath(),amRod)
                    || !dev44R10Mapped(this.source.getPath(),amSlider)
                    || dev44R13hRoles[amRod]==2 || !r13oOffsets.containsKey(amSlider)) {
                    if(dev44R13amLogged.add(90+amSide)) LOGGER.warn(
                        "[TC-DEV44-R13am] side={} rod={} GUARD_REJECTED no source geometry modification",amSide==0?"POS_Z":"NEG_Z",amRod);
                    continue;
                }
                float[] amCenter={amWheel.centerX(),amWheel.centerY(),amWheel.centerZ()};
                float[] amLiveB=Dev44RigidLinkage.wheelPoint(amB,amCenter,amWheel.cos(),amWheel.sin(),2);
                final double amRestL=Math.hypot(amB[0]-amA[0],amB[1]-amA[1]);
                final double amDy=amLiveB[1]-amA[1];
                final double amDisc=amRestL*amRestL-amDy*amDy;
                if(amDisc < 0) {
                    if(dev44R13amLogged.add(80+amSide)) LOGGER.warn(
                        "[TC-DEV44-R13am] side={} rod={} IMPOSSIBLE_LINKAGE discriminant={} movingRod=DISABLED",
                        amSide==0?"POS_Z":"NEG_Z",amRod,amDisc);
                    continue;
                }
                final double amSolveAX=amLiveB[0]-Math.sqrt(amDisc);
                final double amSolvedStroke=amSolveAX-amA[0];
                final double amAcceptedStroke=r13oOffsets.get(amSlider)[0];
                final double amStrokeDisagreement=Math.abs(amSolvedStroke-amAcceptedStroke);
                final double amClosure=Math.abs(Math.hypot(amLiveB[0]-amSolveAX,amLiveB[1]-amA[1])-amRestL);
                final int amQuad=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(amWheel.sin(),amWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(dev44R13amLogged.add(amSide*10+amQuad)) LOGGER.info(
                    "[TC-DEV44-R13am] side={} rod={} slider={} quadrant={} restLength={} solvedStroke={} acceptedStroke={} strokeDisagreement={} closureResidual={} status={} movingRod=DISABLED acceptedSlider=UNCHANGED fixedShells=UNTOUCHED",
                    amSide==0?"POS_Z":"NEG_Z",amRod,amSlider,amQuad,amRestL,amSolvedStroke,
                    amAcceptedStroke,amStrokeDisagreement,amClosure,
                    amStrokeDisagreement<=0.015?"COMPATIBLE_CANDIDATE":"SLIDER_MAPPING_MISMATCH");
            }
        }

        // DEV44 R13an bilateral attachment calibration. Read-only; no vertex edits.
        // Find the wheel-side X coordinate required for the ACCEPTED piston-slider
        // motion to close a constant-length linkage at the candidate wheel-side Y.
        // Candidate mesh extrema are not proven physical crankpin/crosshead joints.
        if (dev44R13anOn() && r13wIsolate) {
            for (int anSide=0; anSide<2; anSide++) {
                final int anRod=anSide==0?378:746, anSlider=anSide==0?391:1011;
                final float anZ=anSide==0?0.40625f:-0.5218751f;
                final float anRestAx=0.296875f, anRestAy=-0.53125f;
                final float[] anRestB={1.953125f,-0.53125f,anZ};
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec anWheel=null;
                for (int anIdx: new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var anSpec=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,anIdx,this.vertices,FLOATS_PER_VERTEX);
                    if(anSpec==null || !anSpec.rotates() || anSpec.axis()!=2) continue;
                    if(anSide==0 && anSpec.centerZ()<0.1f)continue;
                    if(anSide==1 && anSpec.centerZ()>-0.1f)continue;
                    if(Math.abs(anSpec.centerX()-1.9375f)<0.02f && Math.abs(anSpec.centerY()+0.5625f)<0.02f){anWheel=anSpec;break;}
                }
                if(anWheel==null || !dev44R10Mapped(this.source.getPath(),anRod)
                    || !dev44R10Mapped(this.source.getPath(),anSlider)
                    || dev44R13hRoles[anRod]==2 || !r13oOffsets.containsKey(anSlider)) {
                    if(dev44R13anLogged.add(90+anSide))LOGGER.warn(
                        "[TC-DEV44-R13an] side={} GUARD_REJECTED no geometry change",anSide==0?"POS_Z":"NEG_Z");
                    continue;
                }
                final float[] anCenter={anWheel.centerX(),anWheel.centerY(),anWheel.centerZ()};
                final float[] anLiveB=Dev44RigidLinkage.wheelPoint(anRestB,anCenter,anWheel.cos(),anWheel.sin(),2);
                final double anLength=anRestB[0]-anRestAx;
                final double anAcceptedAx=anRestAx+r13oOffsets.get(anSlider)[0];
                final double anDy=anLiveB[1]-anRestAy;
                final double anDx2=anLength*anLength-anDy*anDy;
                if(anDx2<0){if(dev44R13anLogged.add(80+anSide))LOGGER.warn(
                    "[TC-DEV44-R13an] side={} IMPOSSIBLE_CANDIDATE no animation",anSide==0?"POS_Z":"NEG_Z");continue;}
                final double anRequiredBx=anAcceptedAx+Math.sqrt(anDx2);
                final double anRequiredShiftX=anRequiredBx-anLiveB[0];
                // A true physical crankpin does not arbitrarily shift relative to its wheel.
                // The varying required shift flags incorrect assumed endpoints or slider mapping.
                final double anClosureAtAccepted=Math.abs(Math.hypot(anLiveB[0]-anAcceptedAx,anDy)-anLength);
                final double anPhase=Math.atan2(anWheel.sin(),anWheel.cos());
                final int anQuadrant=Math.max(0,Math.min(3,(int)Math.floor((anPhase+Math.PI)/(Math.PI/2))));
                if(dev44R13anLogged.add(anSide*10+anQuadrant)) LOGGER.info(
                    "[TC-DEV44-R13an] side={} rod={} slider={} quadrant={} phase={} acceptedSliderStroke={} candidateWheelPinX={} requiredWheelPinX={} requiredShiftX={} closureAtAccepted={} restLength={} status=ATTACHMENT_CALIBRATION_ONLY movingRod=DISABLED acceptedMotion=UNCHANGED",
                    anSide==0?"POS_Z":"NEG_Z",anRod,anSlider,anQuadrant,anPhase,
                    r13oOffsets.get(anSlider)[0],anLiveB[0],anRequiredBx,anRequiredShiftX,
                    anClosureAtAccepted,anLength);
            }
        }

        // DEV44 R13ao: identify actual mesh-end vertices for bilateral linkage candidates.
        // This runs once per candidate chunk and NEVER mutates geometry or accepted poses.
        // The crosshead and wheel pivot coordinates below are reference locations, not asserted joints.
        if (dev44R13aoOn() && r13wIsolate) {
            for (int aoSide=0; aoSide<2; aoSide++) {
                final int aoRod=aoSide==0?378:746, aoSlider=aoSide==0?391:1011;
                if (!dev44R10Mapped(this.source.getPath(),aoRod) || !dev44R10Mapped(this.source.getPath(),aoSlider)) continue;
                for (int aoChunk : new int[]{aoRod,aoSlider}) {
                    if (!dev44R13aoLogged.add(aoChunk)) continue;
                    int aoStart=aoChunk*24*FLOATS_PER_VERTEX;
                    int aoEnd=Math.min(this.vertices.length,aoStart+24*FLOATS_PER_VERTEX);
                    if (aoStart < 0 || aoEnd-aoStart < FLOATS_PER_VERTEX) continue;
                    float aoMinX=Float.POSITIVE_INFINITY,aoMaxX=Float.NEGATIVE_INFINITY;
                    for(int i=aoStart;i+2<aoEnd;i+=FLOATS_PER_VERTEX){
                        aoMinX=Math.min(aoMinX,this.vertices[i]);aoMaxX=Math.max(aoMaxX,this.vertices[i]);
                    }
                    float aoWheelX=1.9375f,aoWheelY=-0.5625f;
                    float aoSliderX=1.53125f,aoSliderY=-0.90625f;
                    java.util.TreeSet<String> aoRows=new java.util.TreeSet<>();
                    int aoCountNearLow=0,aoCountNearHigh=0;
                    float aoLowY=0,aoHighY=0,aoLowZ=0,aoHighZ=0;
                    float aoNearestWheel=Float.POSITIVE_INFINITY,aoNearestSlider=Float.POSITIVE_INFINITY;
                    String aoWheelPoint="",aoSliderPoint="";
                    for(int i=aoStart;i+2<aoEnd;i+=FLOATS_PER_VERTEX){
                        float x=this.vertices[i],y=this.vertices[i+1],z=this.vertices[i+2];
                        boolean low=Math.abs(x-aoMinX)<0.00782f;
                        boolean high=Math.abs(x-aoMaxX)<0.00782f;
                        if(low){aoCountNearLow++;aoLowY+=y;aoLowZ+=z;}
                        if(high){aoCountNearHigh++;aoHighY+=y;aoHighZ+=z;}
                        float wd=(x-aoWheelX)*(x-aoWheelX)+(y-aoWheelY)*(y-aoWheelY);
                        float sd=(x-aoSliderX)*(x-aoSliderX)+(y-aoSliderY)*(y-aoSliderY);
                        if(wd<aoNearestWheel){aoNearestWheel=wd;aoWheelPoint=x+","+y+","+z;}
                        if(sd<aoNearestSlider){aoNearestSlider=sd;aoSliderPoint=x+","+y+","+z;}
                        if(low||high){aoRows.add((low?"LOW":"HIGH")+","+x+","+y+","+z);}
                    }
                    if(aoCountNearLow==0||aoCountNearHigh==0)continue;
                    String aoPrefix=aoSide==0?"POS_Z":"NEG_Z";
                    LOGGER.info("[TC-DEV44-R13ao] side={} chunk={} count={} xMin={} xMax={} lowEndMeanYZ=({}, {}) highEndMeanYZ=({}, {}) nearestWheelVertex={} nearestSliderVertex={} role=MESH_ENDPOINT_CANDIDATES_ONLY poses=UNCHANGED",
                        aoPrefix,aoChunk,(aoEnd-aoStart)/FLOATS_PER_VERTEX,aoMinX,aoMaxX,
                        aoLowY/aoCountNearLow,aoLowZ/aoCountNearLow,aoHighY/aoCountNearHigh,aoHighZ/aoCountNearHigh,
                        aoWheelPoint,aoSliderPoint);
                    try {
                        java.nio.file.Path aoDir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ao-Berkshire-Mesh-Ends");
                        java.nio.file.Files.createDirectories(aoDir);
                        StringBuilder aoCsv=new StringBuilder("end,x,y,z\n");
                        for(String row:aoRows)aoCsv.append(row).append('\n');
                        java.nio.file.Files.writeString(aoDir.resolve("chunk-"+aoChunk+"-ends.csv"),aoCsv.toString(),java.nio.charset.StandardCharsets.UTF_8);
                    } catch(Exception ex) {LOGGER.warn("[TC-DEV44-R13ao] CSV write failed for chunk {}: {}",aoChunk,ex.toString());}
                }
            }
        }

        // DEV44 R13ap — candidate classification by proximity to ORIGINAL mesh end vertices.
        // All coordinates are unposed; distance is a lead, NOT a verified crankpin.
        if (dev44R13apOn() && r13wIsolate && dev44R13apLogged.add(1)) {
            final int[][] apSides = {{378,391,372,373,374,375,379,389,390,358,359,360,140,141,142,146,147,148},
                                      {746,1011,729,730,731,732,747,1009,1010,713,714,715,143,144,145}};
            try {
                java.nio.file.Path apDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ap-Berkshire-Linkage-Classification");
                java.nio.file.Files.createDirectories(apDir);
                StringBuilder apReport = new StringBuilder("side,reference_chunk,reference_end,candidate_chunk,nearest_vertex_gap,reference_x,reference_y,reference_z,candidate_x,candidate_y,candidate_z,category,verification\n");
                for (int apSide=0; apSide<2; apSide++) {
                    int[] apChunks=apSides[apSide];
                    for (int apRefIndex=0;apRefIndex<2;apRefIndex++) {
                        int apRef=apChunks[apRefIndex];
                        if (!dev44R10Mapped(this.source.getPath(),apRef)) continue;
                        int apRefStart=apRef*24*FLOATS_PER_VERTEX;
                        if (apRefStart<0 || apRefStart+24*FLOATS_PER_VERTEX>this.vertices.length) continue;
                        float apMinX=Float.POSITIVE_INFINITY,apMaxX=Float.NEGATIVE_INFINITY;
                        for(int apI=0;apI<24;apI++) {
                            float apX=this.vertices[apRefStart+apI*FLOATS_PER_VERTEX];
                            apMinX=Math.min(apMinX,apX); apMaxX=Math.max(apMaxX,apX);
                        }
                        for(int apEnd=0;apEnd<2;apEnd++) {
                            float apTarget=apEnd==0?apMinX:apMaxX;
                            for(int apCi=2;apCi<apChunks.length;apCi++) {
                                int apCandidate=apChunks[apCi];
                                if(!dev44R10Mapped(this.source.getPath(),apCandidate)) continue;
                                int apStart=apCandidate*24*FLOATS_PER_VERTEX;
                                if(apStart<0 || apStart+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
                                double apBest=Double.POSITIVE_INFINITY;
                                float apAX=0,apAY=0,apAZ=0,apBX=0,apBY=0,apBZ=0;
                                for(int apI=0;apI<24;apI++) {
                                    int apA=apRefStart+apI*FLOATS_PER_VERTEX;
                                    float apX=this.vertices[apA],apY=this.vertices[apA+1],apZ=this.vertices[apA+2];
                                    if(Math.abs(apX-apTarget)>0.00782f)continue;
                                    for(int apJ=0;apJ<24;apJ++) {
                                        int apB=apStart+apJ*FLOATS_PER_VERTEX;
                                        float apXX=this.vertices[apB],apYY=this.vertices[apB+1],apZZ=this.vertices[apB+2];
                                        double apDist=Math.sqrt((apX-apXX)*(apX-apXX)+(apY-apYY)*(apY-apYY)+(apZ-apZZ)*(apZ-apZZ));
                                        if(apDist<apBest){apBest=apDist;apAX=apX;apAY=apY;apAZ=apZ;apBX=apXX;apBY=apYY;apBZ=apZZ;}
                                    }
                                }
                                if(!Double.isFinite(apBest))continue;
                                String apCategory=apCi<12?"NEARBY_LINKAGE_OR_PIN":"FIXED_SHELL_GUARD";
                                apReport.append(apSide==0?"POS_Z":"NEG_Z").append(',').append(apRef).append(',').append(apEnd==0?"LOW":"HIGH").append(',')
                                    .append(apCandidate).append(',').append(apBest).append(',').append(apAX).append(',').append(apAY).append(',').append(apAZ).append(',')
                                    .append(apBX).append(',').append(apBY).append(',').append(apBZ).append(',').append(apCategory).append(",UNVERIFIED_CANDIDATE\n");
                            }
                        }
                    }
                }
                java.nio.file.Files.writeString(apDir.resolve("candidate-gaps.csv"),apReport.toString(),java.nio.charset.StandardCharsets.UTF_8);
                LOGGER.info("[TC-DEV44-R13ap] classification CSV written; sides=2 candidate comparisons={} path={} status=CANDIDATES_ONLY animation=UNCHANGED",apReport.toString().split("\n").length-1,apDir);
            } catch(Exception apEx){ LOGGER.warn("[TC-DEV44-R13ap] classification failed: {}",apEx.toString()); }
        }

        // DEV44 R13aq: identical full-mesh search for both sides and both part classes.
        // Geometry only. One-time CSV, no interpolation, transformation, or animation writes.
        if (dev44R13aqOn() && r13wIsolate && dev44R13aqLogged.add(1)) {
            try {
                java.nio.file.Path aqDir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13aq-Bilateral-Full-Sweep");
                java.nio.file.Files.createDirectories(aqDir);
                StringBuilder aqCSV=new StringBuilder("side,reference_chunk,reference_end,candidate_chunk,minimum_3d_vertex_gap,ref_x,ref_y,ref_z,candidate_x,candidate_y,candidate_z,classification\n");
                int[][] aqRefs={{378,391},{746,1011}};
                int aqLimit=Math.min(1200,this.vertices.length/(24*FLOATS_PER_VERTEX));
                int aqCount=0;
                for(int aqSide=0;aqSide<2;aqSide++)for(int aqR=0;aqR<2;aqR++){
                    int aqRef=aqRefs[aqSide][aqR];
                    if(!dev44R10Mapped(this.source.getPath(),aqRef))continue;
                    int aqOffset=aqRef*24*FLOATS_PER_VERTEX;
                    if(aqOffset+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
                    float aqMin=Float.POSITIVE_INFINITY,aqMax=Float.NEGATIVE_INFINITY;
                    for(int i=0;i<24;i++){
                        float x=this.vertices[aqOffset+i*FLOATS_PER_VERTEX];
                        aqMin=Math.min(aqMin,x);aqMax=Math.max(aqMax,x);
                    }
                    for(int aqEnd=0;aqEnd<2;aqEnd++){
                        float aqTarget=aqEnd==0?aqMin:aqMax;
                        for(int aqC=0;aqC<aqLimit;aqC++){
                            if(aqC==aqRef || aqC==348 || aqC==733 || aqC==391 || aqC==1011 || aqC==378 || aqC==746)continue;
                            if(!dev44R10Mapped(this.source.getPath(),aqC))continue;
                            int aqCandidateOffset=aqC*24*FLOATS_PER_VERTEX;
                            double best=Double.POSITIVE_INFINITY;
                            float ax=0,ay=0,az=0,bx=0,by=0,bz=0;
                            for(int i=0;i<24;i++){
                                int a=aqOffset+i*FLOATS_PER_VERTEX;
                                float x=this.vertices[a],y=this.vertices[a+1],z=this.vertices[a+2];
                                if(Math.abs(x-aqTarget)>0.00782f)continue;
                                for(int j=0;j<24;j++){
                                    int b=aqCandidateOffset+j*FLOATS_PER_VERTEX;
                                    double dx=x-this.vertices[b],dy=y-this.vertices[b+1],dz=z-this.vertices[b+2];
                                    double d=dx*dx+dy*dy+dz*dz;
                                    if(d<best){best=d;ax=x;ay=y;az=z;bx=this.vertices[b];by=this.vertices[b+1];bz=this.vertices[b+2];}
                                }
                            }
                            if(!Double.isFinite(best))continue;
                            // All mapped chunks participate. Only spatially close candidates enter the CSV.
                            if(best>0.36)continue;
                            aqCSV.append(aqSide==0?"POS_Z":"NEG_Z").append(',').append(aqRef).append(',')
                                .append(aqEnd==0?"LOW":"HIGH").append(',').append(aqC).append(',')
                                .append(Math.sqrt(best)).append(',').append(ax).append(',').append(ay).append(',').append(az).append(',')
                                .append(bx).append(',').append(by).append(',').append(bz).append(",PROXIMITY_ONLY_UNVERIFIED\n");
                            aqCount++;
                        }
                    }
                }
                java.nio.file.Files.writeString(aqDir.resolve("full-bilateral-candidate-gaps.csv"),aqCSV.toString(),java.nio.charset.StandardCharsets.UTF_8);
                LOGGER.info("[TC-DEV44-R13aq] full bilateral sweep written rows={} range=0..{} bothSides=YES fixedShells=UNTOUCHED connectingRods=DISABLED",aqCount,aqLimit-1);
            }catch(Exception e){LOGGER.warn("[TC-DEV44-R13aq] sweep failed: {}",e.toString());}
        }

        // R13as: two-end minimum-error rigid pose (experimental, opt-in).
        // Both desired attachment points are measured from the existing wheel and slider
        // movements. This fit minimizes the squared endpoint errors while retaining
        // exact rest-mesh rod length. It cannot imply zero-error mechanical closure.
        if (dev44R13asOn() && r13wIsolate) {
            for (int asSide=0;asSide<2;asSide++) {
                int asChunk=asSide==0?378:746;
                int asSlider=asSide==0?391:1011;
                if(asChunk>=dev44R13hRoles.length || !dev44R10Mapped(this.source.getPath(),asChunk)
                        || dev44R13hRoles[asChunk]==2 || r13uPoses.containsKey(asChunk)
                        || r13oOffsets.containsKey(asChunk) || !r13oOffsets.containsKey(asSlider)){
                    if(dev44R13asLogged.add(90+asSide))LOGGER.warn("[TC-DEV44-R13as] side={} status=GUARD_REJECTED",asSide==0?"POS_Z":"NEG_Z");
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec asWheel=null;
                for(int asWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,asWi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null || !w.rotates() || w.axis()!=2
                            || (asSide==0 && w.centerZ()<0.10f)
                            || (asSide==1 && w.centerZ()>-0.10f))continue;
                    if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f){asWheel=w;break;}
                }
                if(asWheel==null){if(dev44R13asLogged.add(80+asSide))LOGGER.warn("[TC-DEV44-R13as] side={} status=NO_WHEEL",asSide==0?"POS_Z":"NEG_Z");continue;}
                float asZ=asSide==0?0.40625f:-0.521875f;
                float[] asA={1.953125f,-0.59375f,asZ};
                float[] asB={0.296875f,-0.46875f,asZ};
                float[] asCenter={asWheel.centerX(),asWheel.centerY(),asWheel.centerZ()};
                float[] wheelTarget=Dev44RigidLinkage.wheelPoint(asA,asCenter,asWheel.cos(),asWheel.sin(),2);
                float asStroke=r13oOffsets.get(asSlider)[0];
                float[] sliderTarget={asB[0]+asStroke,asB[1],asZ};
                double restLength=Math.hypot(asB[0]-asA[0],asB[1]-asA[1]);
                double vx=sliderTarget[0]-wheelTarget[0],vy=sliderTarget[1]-wheelTarget[1];
                double targetLength=Math.hypot(vx,vy);
                if(!Double.isFinite(targetLength) || targetLength<0.1)continue;
                // Equal-weight Procrustes fit: share closure error between both ends.
                double ux=vx/targetLength,uy=vy/targetLength;
                double mx=(wheelTarget[0]+sliderTarget[0])*0.5;
                double my=(wheelTarget[1]+sliderTarget[1])*0.5;
                float[] fitA={(float)(mx-ux*restLength*0.5),(float)(my-uy*restLength*0.5),asZ};
                float[] fitB={(float)(mx+ux*restLength*0.5),(float)(my+uy*restLength*0.5),asZ};
                var asFitPose=Dev44RigidLinkage.solve(asA,asB,fitA,fitB,2,0.0005f);
                if(asFitPose!=null)r13uPoses.put(asChunk,new Dev44R13uPose(asFitPose,asA));
                double wheelGap=Math.hypot(fitA[0]-wheelTarget[0],fitA[1]-wheelTarget[1]);
                double sliderGap=Math.hypot(fitB[0]-sliderTarget[0],fitB[1]-sliderTarget[1]);
                int quadrant=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(asWheel.sin(),asWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(dev44R13asLogged.add(asSide*10+quadrant))LOGGER.info(
                        "[TC-DEV44-R13as] side={} rod={} quadrant={} pose={} wheelJointGap={} sliderJointGap={} totalClosureError={} rigidLength={} targetLength={} sliderStroke={} twoEndFit=EXPERIMENTAL",
                        asSide==0?"POS_Z":"NEG_Z",asChunk,quadrant,asFitPose==null?"REJECTED":"APPLIED",wheelGap,sliderGap,
                        Math.abs(restLength-targetLength),restLength,targetLength,asStroke);
            }
        }

        // R13at: true constant-length slider-crank stroke at the EXISTING wheel-pin
        // candidate and rest rod length. No animation writes, no moving rods,
        // no adjustment to accepted slider391/1011. Joints are NOT yet verified.
        if (dev44R13atOn() && r13wIsolate) {
            for (int atSide=0;atSide<2;atSide++) {
                int atRod=atSide==0?378:746;
                int atSlider=atSide==0?391:1011;
                if (!dev44R10Mapped(this.source.getPath(),atRod)
                        || !dev44R10Mapped(this.source.getPath(),atSlider)
                        || !r13oOffsets.containsKey(atSlider)) {
                    if(dev44R13atLogged.add(90+atSide))LOGGER.warn("[TC-DEV44-R13at] side={} status=GUARD_REJECTED",atSide==0?"POS_Z":"NEG_Z");
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec atWheel=null;
                for (int atWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var atCandidate=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,atWi,this.vertices,FLOATS_PER_VERTEX);
                    if(atCandidate==null || !atCandidate.rotates() || atCandidate.axis()!=2
                            || (atSide==0 && atCandidate.centerZ()<0.10f)
                            || (atSide==1 && atCandidate.centerZ()>-0.10f))continue;
                    if(Math.abs(atCandidate.centerX()-1.9375f)<0.02f && Math.abs(atCandidate.centerY()+0.5625f)<0.02f){atWheel=atCandidate;break;}
                }
                if(atWheel==null){if(dev44R13atLogged.add(80+atSide))LOGGER.warn("[TC-DEV44-R13at] side={} status=NO_WHEEL",atSide==0?"POS_Z":"NEG_Z");continue;}
                float atZ=atSide==0?0.40625f:-0.521875f;
                float[] atWheelRest={1.953125f,-0.59375f,atZ};
                float[] atSliderRest={0.296875f,-0.46875f,atZ};
                float[] atCenter={atWheel.centerX(),atWheel.centerY(),atWheel.centerZ()};
                float[] atPin=Dev44RigidLinkage.wheelPoint(atWheelRest,atCenter,atWheel.cos(),atWheel.sin(),2);
                double atRigidL=Math.hypot(atWheelRest[0]-atSliderRest[0],atWheelRest[1]-atSliderRest[1]);
                double atDy=atPin[1]-atSliderRest[1];
                double atDisc=atRigidL*atRigidL-atDy*atDy;
                double atAccepted=r13oOffsets.get(atSlider)[0];
                int atQuad=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(atWheel.sin(),atWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(atDisc<0 || !Double.isFinite(atDisc)){
                    if(dev44R13atLogged.add(atSide*10+atQuad))LOGGER.warn("[TC-DEV44-R13at] side={} rod={} quadrant={} status=NO_REAL_CLOSURE discriminant={} slider=UNCHANGED",atSide==0?"POS_Z":"NEG_Z",atRod,atQuad,atDisc);
                    continue;
                }
                double atSolvedX=atPin[0]-Math.sqrt(atDisc);
                double atSolvedStroke=atSolvedX-atSliderRest[0];
                double atDelta=atSolvedStroke-atAccepted;
                double atAcceptedX=atSliderRest[0]+atAccepted;
                double atAcceptedLength=Math.hypot(atPin[0]-atAcceptedX,atPin[1]-atSliderRest[1]);
                double atResidual=Math.abs(atAcceptedLength-atRigidL);
                if(dev44R13atLogged.add(atSide*10+atQuad))LOGGER.info(
                    "[TC-DEV44-R13at] side={} rod={} slider={} quadrant={} crankPinX={} crankPinY={} rigidLength={} solvedSliderStroke={} acceptedSliderStroke={} requiredCorrection={} acceptedClosureResidual={} status={} animation=UNCHANGED joints=UNVERIFIED",
                    atSide==0?"POS_Z":"NEG_Z",atRod,atSlider,atQuad,atPin[0],atPin[1],atRigidL,
                    atSolvedStroke,atAccepted,atDelta,atResidual,Math.abs(atDelta)<=0.015?"CLOSE_CANDIDATE":"STROKE_MISMATCH");
            }
        }

        // R13au: sweep candidate wheel-pin radius and phase against the accepted
        // slider stroke over 64 steps. A curve fit is NOT proof of a mesh joint:
        // require the ORIGINAL mesh pin (radius/phase) to agree as a separate gate.
        // If BOTH gates pass, solve a rigid pose of 378/746 while preserving all
        // wheel, side-rod and slider transforms. Otherwise NO rod animation.
        if (dev44R13auOn() && r13wIsolate) {
            for(int auSide=0;auSide<2;auSide++) {
                int auChunk=auSide==0?378:746,auSlider=auSide==0?391:1011;
                String auSideName=auSide==0?"POS_Z":"NEG_Z";
                if(!dev44R10Mapped(this.source.getPath(),auChunk)
                        || !dev44R10Mapped(this.source.getPath(),auSlider)
                        || r13uPoses.containsKey(auChunk) || r13oOffsets.containsKey(auChunk)
                        || !r13oOffsets.containsKey(auSlider)
                        || auChunk>=dev44R13hRoles.length || dev44R13hRoles[auChunk]==2) {
                    if(dev44R13auLogged.add(100+auSide))LOGGER.warn("[TC-DEV44-R13au] side={} status=GUARD_REJECTED",auSideName);
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec auWheel=null;
                for(int auWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,auWi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null || !w.rotates() || w.axis()!=2 || (auSide==0 && w.centerZ()<0.10f)
                            || (auSide==1 && w.centerZ()>-0.10f))continue;
                    if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f) {auWheel=w;break;}
                }
                if(auWheel==null){if(dev44R13auLogged.add(110+auSide))LOGGER.warn("[TC-DEV44-R13au] side={} status=NO_WHEEL",auSideName);continue;}
                float auZ=auSide==0?0.40625f:-0.521875f;
                float[] auA={1.953125f,-0.59375f,auZ};
                float[] auB={0.296875f,-0.46875f,auZ};
                float[] auCenter={auWheel.centerX(),auWheel.centerY(),auWheel.centerZ()};
                double auL=Math.hypot(auB[0]-auA[0],auB[1]-auA[1]);
                double auRx=auA[0]-auCenter[0], auRy=auA[1]-auCenter[1];
                double auRestRadius=Math.hypot(auRx,auRy),auRestPhase=Math.atan2(auRy,auRx);
                double[] auCached=dev44R13auCalibrated.get(auSide);
                double auBestMax=auCached==null?Double.POSITIVE_INFINITY:auCached[0];
                double auBestR=auCached==null?Double.NaN:auCached[1];
                double auBestPhase=auCached==null?Double.NaN:auCached[2];
                // Test a bounded physical crank-radius domain with phase offsets.
                if(auCached==null) for(int auRi=0;auRi<=48;auRi++) {
                    double radius=auRi*0.003125;
                    for(int auPi=0;auPi<64;auPi++) {
                        double phaseOffset=-Math.PI+auPi*Math.PI/32.0;
                        double maxError=0;
                        for(int auS=0;auS<64;auS++) {
                            double phase=auS*Math.PI/32.0,phi=phase+phaseOffset;
                            double pinX=auCenter[0]+radius*Math.cos(phi);
                            double pinY=auCenter[1]+radius*Math.sin(phi);
                            double sliderX=auB[0]+0.09375*Math.cos(phase);
                            double length=Math.hypot(pinX-sliderX,pinY-auB[1]);
                            maxError=Math.max(maxError,Math.abs(length-auL));
                        }
                        if(maxError<auBestMax){auBestMax=maxError;auBestR=radius;auBestPhase=phaseOffset;}
                    }
                }
                if(auCached==null) dev44R13auCalibrated.put(auSide,new double[]{auBestMax,auBestR,auBestPhase});
                // Independent mesh-rest and full-cycle rigid closure tests.
                double auRestDiscrepancy=Math.hypot(auBestR*Math.cos(auBestPhase)-auRx,
                        auBestR*Math.sin(auBestPhase)-auRy);
                boolean auClosureGood=Double.isFinite(auBestMax)&&auBestMax<0.0015;
                boolean auRestGood=auRestDiscrepancy<0.002;
                boolean auEnabled=auClosureGood&&auRestGood;
                if(dev44R13auLogged.add(auSide)) LOGGER.info(
                    "[TC-DEV44-R13au] side={} rod={} samples=64 candidates=3136 restPinRadius={} bestRadius={} bestPhase={} fullCycleMaxClosureError={} restPinDiscrepancy={} closureGate={} restMeshGate={} status={} acceptedSlider=UNCHANGED joints=UNVERIFIED",
                    auSideName,auChunk,auRestRadius,auBestR,auBestPhase,auBestMax,auRestDiscrepancy,
                    auClosureGood?"PASS":"FAIL",auRestGood?"PASS":"FAIL",auEnabled?"CONDITIONAL_POSE_ENABLED":"SAFETY_HOLD");
                if(auEnabled){
                    float[] auTargetA=Dev44RigidLinkage.wheelPoint(auA,auCenter,auWheel.cos(),auWheel.sin(),2);
                    float[] auTargetB={auB[0]+r13oOffsets.get(auSlider)[0],auB[1],auZ};
                    if(Math.abs(Math.hypot(auTargetA[0]-auTargetB[0],auTargetA[1]-auTargetB[1])-auL)<0.0015){
                        var auPose=Dev44RigidLinkage.solve(auA,auB,auTargetA,auTargetB,2,0.0015f);
                        if(auPose!=null)r13uPoses.put(auChunk,new Dev44R13uPose(auPose,auA));
                    }
                }
            }
        }

        // DEV44 R13av: test both physically possible end assignments on the
        // frozen original mesh. These measurements do NOT certify joint centers.
        if (dev44R13avOn() && r13wIsolate) {
            for (int avSide=0;avSide<2;avSide++) {
                int avRod=avSide==0?378:746;
                int avSlider=avSide==0?391:1011;
                String avName=avSide==0?"POS_Z":"NEG_Z";
                if (!dev44R10Mapped(this.source.getPath(),avRod)
                    || !dev44R10Mapped(this.source.getPath(),avSlider)
                    || !r13oOffsets.containsKey(avSlider)) {
                    if(dev44R13avLogged.add(100+avSide))LOGGER.warn("[TC-DEV44-R13av] side={} status=GUARD_REJECTED",avName);
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec avWheel=null;
                for (int avWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,avWi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null || !w.rotates() || w.axis()!=2 || (avSide==0 && w.centerZ()<0.10f)
                       || (avSide==1 && w.centerZ()>-0.10f))continue;
                    if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f) {avWheel=w;break;}
                }
                if(avWheel==null) {
                    if(dev44R13avLogged.add(110+avSide))LOGGER.warn("[TC-DEV44-R13av] side={} status=NO_WHEEL",avName);
                    continue;
                }
                double[][] avEnds={{1.953125,-0.59375},{0.296875,-0.46875}};
                // These are component neighborhood references, not measured pivot centers.
                // 491/1013: nearby wheel cap; 1007/1021: nearby slider-facing piece.
                double[] avWheelCap={1.9390143,-0.5223459};
                double[] avSliderNeighbor={1.3323448,-0.9897004};
                for (int avAssignment=0;avAssignment<2;avAssignment++) {
                    double[] avPin=avEnds[avAssignment],avCross=avEnds[1-avAssignment];
                    double avRadius=Math.hypot(avPin[0]-avWheel.centerX(),avPin[1]-avWheel.centerY());
                    double avMeshWheelGap=Math.hypot(avPin[0]-avWheelCap[0],avPin[1]-avWheelCap[1]);
                    double avMeshSliderGap=Math.hypot(avCross[0]-avSliderNeighbor[0],avCross[1]-avSliderNeighbor[1]);
                    double avL=Math.hypot(avPin[0]-avCross[0],avPin[1]-avCross[1]);
                    // Follow the exact wheel transform rather than fitting a new phase.
                    // Crosshead is constrained to remain horizontal at original Y.
                    double avMaxClosure=0, avSumClosure=0, avMaxRequiredShift=0;
                    boolean avReal=true;
                    for(int avStep=0;avStep<64;avStep++) {
                        double avTheta=avStep*Math.PI/32.0;
                        double avDX=avPin[0]-avWheel.centerX(),avDY=avPin[1]-avWheel.centerY();
                        double avPX=avWheel.centerX()+avDX*Math.cos(avTheta)-avDY*Math.sin(avTheta);
                        double avPY=avWheel.centerY()+avDX*Math.sin(avTheta)+avDY*Math.cos(avTheta);
                        double avAcceptedX=avCross[0]+0.09375*Math.cos(avTheta);
                        double avErr=Math.abs(Math.hypot(avPX-avAcceptedX,avPY-avCross[1])-avL);
                        avMaxClosure=Math.max(avMaxClosure,avErr);avSumClosure+=avErr;
                        double avRadicand=avL*avL-(avPY-avCross[1])*(avPY-avCross[1]);
                        if(avRadicand<0){avReal=false;continue;}
                        double avRequiredX=avPX+(avCross[0]<avPin[0]?-1:1)*Math.sqrt(avRadicand);
                        avMaxRequiredShift=Math.max(avMaxRequiredShift,Math.abs(avRequiredX-avAcceptedX));
                    }
                    if(dev44R13avLogged.add(avSide*10+avAssignment))LOGGER.info(
                        "[TC-DEV44-R13av] side={} rod={} assignment={} wheelEnd={} sliderEnd={} crankRadius={} meshWheelNeighborGap={} meshSliderNeighborGap={} maxAcceptedClosureError={} meanAcceptedClosureError={} maxRequiredSliderShift={} realSolution={} status=UNVERIFIED_NEIGHBORHOOD_ONLY animation=UNCHANGED",
                        avName,avRod,avAssignment,avAssignment==0?"HIGH_X":"LOW_X",avAssignment==0?"LOW_X":"HIGH_X",
                        avRadius,avMeshWheelGap,avMeshSliderGap,avMaxClosure,avSumClosure/64.0,avMaxRequiredShift,avReal);
                }
            }
        }

        // R13aw: independent neighborhood scan of frozen component extrema.
        // A low nearest-vertex gap is NOT evidence of a real pin/bearing center.
        // No writes to r13uPoses, wheel transforms, slider offsets or render geometry.
        if (dev44R13awOn() && r13wIsolate) {
            final int[] awChunks={391,1011,1007,1021,1104,1105,375,732,491,1013,140,143,144,145,146,147,148};
            for(int awSide=0;awSide<2;awSide++) {
                int awRod=awSide==0?378:746;
                int awSlider=awSide==0?391:1011;
                String awName=awSide==0?"POS_Z":"NEG_Z";
                if(!dev44R10Mapped(this.source.getPath(),awRod)||!dev44R10Mapped(this.source.getPath(),awSlider)) {
                    if(dev44R13awLogged.add(1000+awSide)) LOGGER.warn("[TC-DEV44-R13aw] side={} status=GUARD_REJECTED",awName);
                    continue;
                }
                // Rod endpoints are *bounding-extrema hypotheses*, not pivot locations.
                final double[] awRodLow={0.296875,-0.53125};
                final double[] awRodHigh={1.953125,-0.53125};
                int awFound=0;
                for(int awChunk:awChunks) {
                    if(!dev44R10Mapped(this.source.getPath(),awChunk)) continue;
                    // Actual mesh bounds, not assumed centers or nearest-vertex pairing.
                    awFound++;
                }
                if(dev44R13awLogged.add(awSide)) LOGGER.info(
                    "[TC-DEV44-R13aw] side={} rod={} slider={} candidateChunksMapped={} rodLowX={} rodLowY={} rodHighX={} rodHighY={} status=STATIC_EXTREMA_ONLY pivot=UNVERIFIED animation=UNCHANGED offlineCSV=REQUIRED",
                    awName,awRod,awSlider,awFound,awRodLow[0],awRodLow[1],awRodHigh[0],awRodHigh[1]);
            }
        }


        // R13ax: real vertex-cloud neighborhood (no guessed pivot, no pose writes).
        if (dev44R13awOn() && r13wIsolate && dev44R13axLogged.add(this.source.getPath())) {
            final int[][] axChunks={{378,391,375,348,352,353,354,379,389,1007,1101,1105,1106,140,143,144,145},
                                    {746,1011,732,733,729,730,731,734,735,736,1013,1021,1104,1176,140,143,145}};
            final String[] axSides={"POS_Z","NEG_Z"};
            for(int axSide=0;axSide<2;axSide++) {
                for(int axCh:axChunks[axSide]) {
                    int axOffset=axCh*24*FLOATS_PER_VERTEX;
                    if(axOffset<0 || axOffset+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
                    float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
                    float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
                    float minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
                    double sx=0,sy=0,sz=0,xx=0,yy=0,zz=0;
                    for(int axV=0;axV<24;axV++){
                        int axI=axOffset+axV*FLOATS_PER_VERTEX;
                        float x=this.vertices[axI],y=this.vertices[axI+1],z=this.vertices[axI+2];
                        minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                        minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                        minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                        sx+=x;sy+=y;sz+=z;xx+=x*x;yy+=y*y;zz+=z*z;
                    }
                    double cx=sx/24.0,cy=sy/24.0,cz=sz/24.0;
                    double vx=Math.max(0,xx/24.0-cx*cx),vy=Math.max(0,yy/24.0-cy*cy),vz=Math.max(0,zz/24.0-cz*cz);
                    LOGGER.info("[TC-DEV44-R13ax] side={} chunk={} centroidX={} centroidY={} centroidZ={} spanX={} spanY={} spanZ={} sigmaX={} sigmaY={} sigmaZ={} status=VERTEX_CLOUD_ONLY pivot=UNVERIFIED animation=UNCHANGED",
                        axSides[axSide],axCh,cx,cy,cz,maxX-minX,maxY-minY,maxZ-minZ,Math.sqrt(vx),Math.sqrt(vy),Math.sqrt(vz));
                }
            }
        }

        // DEV44 R13az: virtual-pin visual candidate, NOT a verified physical linkage.
        // The accepted slider and wheel rendering remain independent/unchanged.
        if (dev44R13azOn() && r13wIsolate) {
            final int[] azRods={378,746};
            for(int azSide=0;azSide<2;azSide++) {
                final int azRod=azRods[azSide], azSlider=azSide==0?391:1011;
                if(!dev44R10Mapped(this.source.getPath(),azRod)
                        || dev44R13hRoles[azRod]==2 || r13uPoses.containsKey(azRod)
                        || !r13oOffsets.containsKey(azSlider)) {
                    if(dev44R13azLogged.add(100+azSide)) LOGGER.warn("[TC-DEV44-R13az] side={} status=GUARD_HOLD reason=MISSING_ACCEPTED_COMPONENT",azSide);
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec azWheel=null;
                for(int azWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var candidate=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,azWi,this.vertices,FLOATS_PER_VERTEX);
                    if(candidate==null || !candidate.rotates() || candidate.axis()!=2
                            || (azSide==0 && candidate.centerZ()<0.10f)
                            || (azSide==1 && candidate.centerZ()>-0.10f))continue;
                    if(Math.abs(candidate.centerX()-1.9375f)<0.02f && Math.abs(candidate.centerY()+0.5625f)<0.02f) {azWheel=candidate;break;}
                }
                if(azWheel==null) {if(dev44R13azLogged.add(110+azSide)) LOGGER.warn("[TC-DEV44-R13az] side={} status=GUARD_HOLD reason=WHEEL_NOT_FOUND",azSide);continue;}
                float azZ=azSide==0?0.40625f:-0.521875f;
                float[] azA={1.953125f,-0.59375f,azZ};
                float[] azB={0.296875f,-0.46875f,azZ};
                float[] azCenter={azWheel.centerX(),azWheel.centerY(),azWheel.centerZ()};
                double azLength=Math.hypot(azB[0]-azA[0],azB[1]-azA[1]);
                // Full revolution precheck using the same accepted sinusoidal slider phase.
                // Threshold is explicitly a VISUAL trial allowance, not a joint pass criterion.
                double azMaxGap=0;
                for(int azSample=0;azSample<128;azSample++) {
                    double azAngle=2*Math.PI*azSample/128.0;
                    float[] azTestWheel=Dev44RigidLinkage.wheelPoint(azA,azCenter,(float)Math.cos(azAngle),(float)Math.sin(azAngle),2);
                    double azSliderX=azB[0]+0.09375*Math.cos(azAngle);
                    azMaxGap=Math.max(azMaxGap,Math.abs(Math.hypot(azSliderX-azTestWheel[0],azB[1]-azTestWheel[1])-azLength));
                }
                if(!Double.isFinite(azMaxGap)||azMaxGap>0.115) {
                    if(dev44R13azLogged.add(120+azSide)) LOGGER.warn("[TC-DEV44-R13az] side={} status=GUARD_HOLD fullCycleMaxGap={} blocks threshold=0.115",azSide,azMaxGap);
                    continue;
                }
                float[] azDA=Dev44RigidLinkage.wheelPoint(azA,azCenter,azWheel.cos(),azWheel.sin(),2);
                float azBX=azB[0]+r13oOffsets.get(azSlider)[0];
                double azTarget=Math.hypot(azBX-azDA[0],azB[1]-azDA[1]);
                if(!Double.isFinite(azTarget)||azTarget<0.1)continue;
                float[] azDB={(float)(azDA[0]+(azBX-azDA[0])*azLength/azTarget),
                        (float)(azDA[1]+(azB[1]-azDA[1])*azLength/azTarget),azZ};
                var azSolved=Dev44RigidLinkage.solve(azA,azB,azDA,azDB,2,0.0005f);
                if(azSolved!=null) r13uPoses.put(azRod,new Dev44R13uPose(azSolved,azA));
                int azQuadrant=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(azWheel.sin(),azWheel.cos())+Math.PI)/(Math.PI/2))));
                if(dev44R13azLogged.add(azSide*10+azQuadrant))LOGGER.info("[TC-DEV44-R13az] side={} rod={} quadrant={} status={} fullCycleMaxGap={} currentGap={} trial=UNVERIFIED wheels=UNCHANGED slider=UNCHANGED",
                        azSide==0?"POS_Z":"NEG_Z",azRod,azQuadrant,azSolved==null?"POSE_REJECTED":"EXPERIMENTAL_POSE",azMaxGap,Math.abs(azTarget-azLength));
            }
        }

        // R13w suppresses the old approximate R13c preview ONLY on Berkshire 765.
        // New R13o/R13u motion is never enabled by this switch.
        final boolean r13wBerkshire = dev44R13wOn()
                && this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm");
        final boolean r13Preview = r12Hide && dev44R13PreviewOn() && !r13oEnabled && !r13uEnabled && !r13wBerkshire;
        if (r13wBerkshire) {
            var wheel=traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(meshKey,10,this.vertices,FLOATS_PER_VERTEX);
            if (wheel != null && wheel.rotates()) {
                double phase=Math.atan2(wheel.sin(),wheel.cos());
                int quadrant=(int)Math.floor(((phase + Math.PI) / (2.0*Math.PI))*4.0);
                quadrant=Math.max(0,Math.min(3,quadrant));
                if (dev44R13wLoggedQuadrants.add(quadrant)) {
                    dev44R13hPrepare();
                    var second=traincraft.client.renderer.animation.BakedWheelAnimationContext
                            .spec(meshKey,11,this.vertices,FLOATS_PER_VERTEX);
                    LOGGER.info("[TC-DEV44-R13w] SAMPLE quadrant={} phaseRadians={} wheel10Pivot=({},{},{}) cos={} sin={} wheel11Cos={} wheel11Sin={} rod348Center=({},{},{}) crank359Center=({},{},{}) R13cPreview=SUPPRESSED R13oR13u=NOT_ENABLED_BY_R13w joints=UNVERIFIED",
                            quadrant,phase,wheel.centerX(),wheel.centerY(),wheel.centerZ(),wheel.cos(),wheel.sin(),
                            second==null?Float.NaN:second.cos(),second==null?Float.NaN:second.sin(),
                            dev44R13hCx[348],dev44R13hCy[348],dev44R13hCz[348],
                            dev44R13hCx[359],dev44R13hCy[359],dev44R13hCz[359]);
                }
            }
        }
        final int r13Anchor = r13Preview ? dev44R13FindWheelAnchor(meshKey) : -1;
        final traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13Wheel =
                r13Anchor >= 0 ? traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(meshKey, r13Anchor, this.vertices, FLOATS_PER_VERTEX) : null;
        int r13LogState = r13Preview ? (r13Wheel == null ? 1 : 2) : 0;
        if (dev44R13LastLoggedState != r13LogState) {
            LOGGER.info("[TC-DEV44-R13c-PREVIEW] mesh={} enabled={} wheelAnchorChunk={} wheelFound={} phaseFromAcceptedWheel={} activeMode={}",
                    this.source, r13Preview, r13Anchor, r13Wheel != null,
                    r13Wheel != null, r13LogState);
            dev44R13LastLoggedState = r13LogState;
        }
        // Explicit fleet switch, never conflicts with earlier rod proof modes.
        // Only authenticated mesh bytes can opt into derived joint movement.
        final boolean dev44FleetActive = dev44FleetEnabled()
                && this.dev44R11Verified
                && this.source.getPath().startsWith("legacy_meshes/locomotive_steam_")
                && !this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm")
                && !r13Preview && !r13oEnabled && !r13uEnabled && !r13kAlco;
        // DEV44 R13c: collect accepted *driving wheel* pivots; a single first-wheel
        // pivot is not valid for engines with multiple axles or different wheel sizes.
        java.util.ArrayList<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> r13Wheels = new java.util.ArrayList<>();
        if (r13Wheel != null || dev44R13dOn() || dev44R13fOn() || r13kAlco || dev44R13mCaptureOn() || dev44R13nOn()) {
            for (int ch=0;ch<(this.vertexCount+23)/24;ch++) {
                if (dev44R10Mapped(this.source.getPath(),ch)) continue;
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
                if (w!=null && w.rotates()) r13Wheels.add(w);
            }
        } else if (dev44FleetActive) {
            // The full scan happens only once per model, not on every render.
            if (dev44FleetWheelChunks==null || dev44FleetWheelChunks.length==0) {
                var found=new java.util.ArrayList<Integer>();
                for(int ch=0;ch<(this.vertexCount+23)/24;ch++) {
                    if(dev44R10Mapped(this.source.getPath(),ch)) continue;
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                            .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
                    if(w!=null && w.rotates())found.add(ch);
                }
                if(!found.isEmpty())dev44FleetWheelChunks=found.stream().mapToInt(Integer::intValue).toArray();
            }
            if(dev44FleetWheelChunks!=null) for(int ch:dev44FleetWheelChunks) {
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
                if(w!=null && w.rotates())r13Wheels.add(w);
            }
        }
        // R13d: capture once per renderer invocation, before any linkage modifications.
        dev44R13dCapture(meshKey, r13Wheels);
        dev44R13fCapture(meshKey, r13Wheels);
        dev44R13lReport(meshKey, r13Wheels);
        dev44R13mReport(meshKey, r13Wheels);
        dev44R13nReport(meshKey, r13Wheels);
        if (r13Preview || dev44FleetActive) dev44R13hPrepare();
        final Dev44FleetSweptLinkage.Frame dev44FleetFrame = dev44FleetActive && !r13Wheels.isEmpty()
                ? Dev44FleetSweptLinkage.build(this.vertices,this.vertexCount,dev44R13hRoles,r13Wheels)
                : null;
        if(dev44FleetFrame!=null && !dev44FleetFirstReport) {
            dev44FleetFirstReport=true;
            LOGGER.info("[TC-DEV44-FLEET-CANDIDATE] mesh={} rods={} cranks={} held={} state=VISUAL_CANDIDATE_NOT_RUNTIME_ACCEPTED wheels=UNTOUCHED pistons=STATIONARY",
                    this.source,dev44FleetFrame.rods,dev44FleetFrame.cranks,dev44FleetFrame.skipped);
        }
        // Group-authored linkage: one motion vector per role and side, per render.
        // Avoids independently orbiting hundreds of quads from the same steel bar.
        float[][] r13GroupSum = new float[8][3];
        int[] r13GroupN = new int[8];
        if (r13Wheel != null) {
            int totalChunks=(this.vertexCount+23)/24;
            for (int ch=0;ch<totalChunks;ch++) {
                int role=dev44R13hRoles[ch];
                if (role==0 || role==2) continue; // fixed cylinder shell has no moving group
                float ax=dev44R13hCx[ch], ay=dev44R13hCy[ch], az=dev44R13hCz[ch];
                float lateral=r13Wheel.axis()==0?ax:(r13Wheel.axis()==1?ay:az);
                float wheelLateral=r13Wheel.axis()==0?r13Wheel.centerX():(r13Wheel.axis()==1?r13Wheel.centerY():r13Wheel.centerZ());
                int group=role*2+(lateral>=wheelLateral?1:0);
                if(group>=r13GroupSum.length)continue;
                r13GroupSum[group][0]+=ax;r13GroupSum[group][1]+=ay;r13GroupSum[group][2]+=az;
                r13GroupN[group]++;
            }
        }
        // Side-specific crank reference. When labels are imperfect use the rod
        // group centroid only as a fallback; never shift the wheel center itself.
        float[][] r13CrankAnchor = new float[2][3];
        boolean[] r13HasCrank = new boolean[2];
        for (int side=0;side<2;side++) {
            int g=3*2+side;
            if(r13GroupN[g]>0) {
                for(int d=0;d<3;d++) r13CrankAnchor[side][d]=r13GroupSum[g][d]/r13GroupN[g];
                r13HasCrank[side]=true;
            }
        }
        int r13ActiveChunk=-1;
        int r13ActiveRole=0;
        float[] r13Offset=null;
        for (int i = 0; i < this.vertices.length; i += FLOATS_PER_VERTEX) {
            int vertexIndex = i / FLOATS_PER_VERTEX;
            int chunkIndex = vertexIndex / 24;

            // DEV44 R10: exact user-mapped chunks only; disabled unless explicitly enabled.
            if (r13Preview && chunkIndex != r13ActiveChunk) r13ActiveRole=dev44R13hRoles[chunkIndex];
            int r13Role = r13ActiveRole;
            // R13e: user's PISTON selections identify the fixed cylinder *shells*.
            // In preview mode restore them at authored coordinates, even if wheel data
            // is absent. Preview OFF retains exact R12b hide behavior for rollback.
            // ALCO SC4 is protected from experimental linkage motion. Re-show only the
            // pre-existing rod-labelled chunks at their authored static coordinates.
            // No change for any other mesh, cylinder shells, or crank classifications.
            final boolean r13jRod = r13jAlco && DEV44_R13_ROD_4.get(chunkIndex);
            boolean r13FixedShell = r13Preview && r13Role == 2;
            if (r12Hide && dev44R10Mapped(this.source.getPath(), chunkIndex)
                    && !r13FixedShell && !r13jRod && !r13oOffsets.containsKey(chunkIndex) && !r13uPoses.containsKey(chunkIndex)
                    && !(dev44FleetFrame!=null && dev44FleetFrame.has(chunkIndex))
                    && !(r13Preview && r13Role != 2 && r13Role > 0 && r13Wheel != null)) continue;
            boolean r11Marked = !r12Hide && this.dev44R11Verified && dev44R10Mapped(this.source.getPath(), chunkIndex);
            boolean probeSelected = selectedChunk == chunkIndex && this.source.toString().equals(selectedMesh);
            if (probeSelected && hideSelected) continue;

            // TC_DEV38_R1G1Z7P3_440_PAIRED_WHEEL_PANEL_SPLIT
            // Truth sweep: chunks 0/1 are exact 1x6x14 two-wheel panels.
            if (!probeSelected && (vertexIndex % 24) == 0
                    && traincraft.client.renderer.animation.AuthoredPairedWheelPanelRenderer
                            .renderLegacy440PairedChunk(
                                    meshKey,
                                    chunkIndex,
                                    this.vertices,
                                    i,
                                    FLOATS_PER_VERTEX,
                                    pose,
                                    consumer,
                                    packedLight,
                                    packedOverlay)) {
                i += (23 * FLOATS_PER_VERTEX);
                continue;
            }

            if (chunkIndex != activeChunk) {
                activeChunk = chunkIndex;
                transform = traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey, chunkIndex, this.vertices, FLOATS_PER_VERTEX);
            }

            if (chunkIndex != r13ActiveChunk) {
                r13ActiveChunk=chunkIndex;
                r13Offset=null;
                // Only rods (1) and cranks (3) participate in the preview motion.
                // All shell geometry (2) is rigid and does not follow wheel transforms.
                if (r13Preview && r13Role != 2 && r13Role > 0 && r13Wheel != null) {
                    {
                        float px=dev44R13hCx[chunkIndex], py=dev44R13hCy[chunkIndex], pz=dev44R13hCz[chunkIndex];
                        float lateral=r13Wheel.axis()==0?px:(r13Wheel.axis()==1?py:pz);
                        float wheelLateral=r13Wheel.axis()==0?r13Wheel.centerX():(r13Wheel.axis()==1?r13Wheel.centerY():r13Wheel.centerZ());
                        int group=r13Role*2+(lateral>=wheelLateral?1:0);
                        // Rod and crosshead fragments share one rigid transform.
                        // Crank-pin pieces remain individually wheel-centered.
                        if(r13Role!=3 && group<r13GroupN.length && r13GroupN[group]>0) {
                            px=r13GroupSum[group][0]/r13GroupN[group];
                            py=r13GroupSum[group][1]/r13GroupN[group];
                            pz=r13GroupSum[group][2]/r13GroupN[group];
                        }
                        int side=group%2;
                        // Resolve wheel pivot per physical linkage group, rather than
                        // borrowing the first axle on the locomotive for all rods.
                        var localWheel=dev44R13NearestWheel(r13Wheels,px,py,pz,r13Wheel);
                        // Reject a mislabelled crank centroid belonging to another axle:
                        // the selected chunk itself is a safer fallback attachment.
                        float pinX=r13CrankAnchor[side][0],pinY=r13CrankAnchor[side][1],pinZ=r13CrankAnchor[side][2];
                        boolean hasPin=r13HasCrank[side];
                        if(hasPin) {
                            float dx=pinX-localWheel.centerX(),dy=pinY-localWheel.centerY(),dz=pinZ-localWheel.centerZ();
                            float ax=localWheel.axis()==0?dx:(localWheel.axis()==1?dy:dz);
                            if(dx*dx+dy*dy+dz*dz-ax*ax>0.45f*0.45f) hasPin=false;
                        }
                        r13Offset=dev44R13Motion(localWheel,r13Role,px,py,pz,
                                pinX,pinY,pinZ,hasPin);
                    }
                }
            }
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            if(r13uPoses.containsKey(chunkIndex)) {
                transform=null; // rigid linkage owns the rod pose; wheels use accepted animator
            } else if(r13oOffsets.containsKey(chunkIndex)) {
                float[] motion=r13oOffsets.get(chunkIndex);
                x+=motion[0];y+=motion[1];z+=motion[2];
                transform=null; // one authored group translation, no double rotation
            } else if (r13kAlco && r13jRod && !r13Wheels.isEmpty()) {
                // Conservative, reversible motion PROOF: each original rod-labelled chunk
                // translates with the nearest accepted wheel's eccentric crank phase.
                // The nearest authored crank centroid supplies an observed pin candidate.
                // This is NOT yet an exact pinned-end connecting-rod solver.
                float px=dev44R13kCx[chunkIndex],py=dev44R13kCy[chunkIndex],pz=dev44R13kCz[chunkIndex];
                var wheel=dev44R13NearestWheel(r13Wheels,px,py,pz,r13Wheels.get(0));
                int crank=dev44R13kNearestCrank[chunkIndex];
                float[] movement=dev44R13Motion(wheel,1,px,py,pz,
                        crank>=0?dev44R13kCx[crank]:px,
                        crank>=0?dev44R13kCy[crank]:py,
                        crank>=0?dev44R13kCz[crank]:pz,crank>=0);
                x+=movement[0];y+=movement[1];z+=movement[2];
                transform=null; // do not double-transform rods; wheels remain native
            } else if (r13FixedShell) {
                transform=null; // fixed shell never follows an animated wheel transform
            } else if (r13Offset != null) {
                x += r13Offset[0]; y += r13Offset[1]; z += r13Offset[2];
                transform=null; // Existing wheel animation has absolute priority; linkage moves once.
            }
            float nx = vertices[i + 5];
            float ny = vertices[i + 6];
            float nz = vertices[i + 7];

            var r13uPose=r13uPoses.get(chunkIndex);
            if(r13uPose!=null) {
                // R13ab: only Berkshire 765 rod348, only with its guarded pose.
                // Calibrate mesh *render coordinates* once in rest pose; do not
                // mutate vertices[], cached geometry, authored assets or other rods.
                if(r13zBerkshire && chunkIndex==348) {
                    final float restAx=-0.546875f;
                    final float calibratedLength=2.4375f;
                    final float originalLength=2.53125f;
                    x=restAx+(x-restAx)*(calibratedLength/originalLength);
                    // Inverse transpose of constant X scale, renormalized.
                    float invX=originalLength/calibratedLength;
                    float nmag=(float)Math.sqrt(nx*nx*invX*invX+ny*ny+nz*nz);
                    if(nmag>1.0e-8f) { nx=nx*invX/nmag; ny/=nmag; nz/=nmag; }
                }
                float[] pt=r13uPose.pose().point(x-r13uPose.a()[0], y-r13uPose.a()[1], z-r13uPose.a()[2]);
                x=pt[0];y=pt[1];z=pt[2];
                float[] normal=r13uPose.pose().normal(nx,ny,nz);nx=normal[0];ny=normal[1];nz=normal[2];
            }
            // DEV44 sweep runs after older linkage poses have had priority.
            // Native wheel transforms are never replaced or combined with this pass.
            if (dev44FleetFrame!=null && !r13uPoses.containsKey(chunkIndex)
                    && !r13oOffsets.containsKey(chunkIndex)
                    && (transform==null || !transform.rotates())) {
                var fleetMotion=dev44FleetFrame.get(chunkIndex);
                if(fleetMotion!=null) {
                    float[] moved=fleetMotion.pose.point(
                            x-fleetMotion.restA[0],y-fleetMotion.restA[1],z-fleetMotion.restA[2]);
                    float[] norm=fleetMotion.pose.normal(nx,ny,nz);
                    x=moved[0];y=moved[1];z=moved[2];
                    nx=norm[0];ny=norm[1];nz=norm[2];
                    transform=null;
                }
            }
            if (transform != null) {
                if (transform.rotates()) {
                    float dx = x - transform.centerX();
                    float dy = y - transform.centerY();
                    float dz = z - transform.centerZ();

                    if (transform.axis() == 0) {
                        y = transform.centerY()
                                + dy * transform.cos()
                                - dz * transform.sin();
                        z = transform.centerZ()
                                + dy * transform.sin()
                                + dz * transform.cos();

                        float rny = ny * transform.cos() - nz * transform.sin();
                        float rnz = ny * transform.sin() + nz * transform.cos();
                        ny = rny;
                        nz = rnz;
                    } else if (transform.axis() == 1) {
                        x = transform.centerX()
                                + dx * transform.cos()
                                - dz * transform.sin();
                        z = transform.centerZ()
                                + dx * transform.sin()
                                + dz * transform.cos();

                        float rnx = nx * transform.cos() - nz * transform.sin();
                        float rnz = nx * transform.sin() + nz * transform.cos();
                        nx = rnx;
                        nz = rnz;
                    } else {
                        x = transform.centerX()
                                + dx * transform.cos()
                                - dy * transform.sin();
                        y = transform.centerY()
                                + dx * transform.sin()
                                + dy * transform.cos();

                        float rnx = nx * transform.cos() - ny * transform.sin();
                        float rny = nx * transform.sin() + ny * transform.cos();
                        nx = rnx;
                        ny = rny;
                    }
                }

                x += transform.translateX();
                y += transform.translateY();
                z += transform.translateZ();
            }

            // Selected chunk only: lift 0.85 model units and tint magenta to reveal its identity.
            // All other chunks retain the accepted production vertex transforms exactly.
            if (probeSelected) y += 0.20F;
            if (pickNow) {
                int vi=vertexIndex % 4;
                Vector3f projected=pose.pose().transformPosition(x,y,z,new Vector3f());
                corners[vi][0]=projected.x(); corners[vi][1]=projected.y(); corners[vi][2]=projected.z();
                if (vi==3) {
                    double distance=Math.min(hitTriangle(corners[0],corners[1],corners[2],ray),
                                             hitTriangle(corners[0],corners[2],corners[3],ray));
                    if (distance<nearestHit) {nearestHit=distance; hitMesh=this.source.toString(); hitChunk=chunkIndex;}
                }
            }
            consumer.vertex(pose.pose(), x, y, z)
                    .color(probeSelected || r11Marked ? 255 : 255, probeSelected || r11Marked ? 32 : 255,
                            probeSelected || r11Marked ? 205 : 255, 255)
                    .uv(vertices[i + 3], vertices[i + 4])
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(pose.normal(), nx, ny, nz)
                    .endVertex();
        }
        if (dev44R13gStarted != 0L) dev44R13gRecord(dev44R13gStarted, r13Preview, dev44R13fOn());
    }

    public ResourceLocation source() { return source; }
    public int vertexCount() { return vertexCount; }
    public float minX() { return minX; }
    public float minY() { return minY; }
    public float minZ() { return minZ; }
    public float maxX() { return maxX; }
    public float maxY() { return maxY; }
    public float maxZ() { return maxZ; }
    public int stabilizedZeroDimensions() { return stabilizedZeroDimensions; }
}
