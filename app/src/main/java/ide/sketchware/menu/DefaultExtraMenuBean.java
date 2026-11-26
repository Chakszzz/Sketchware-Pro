package ide.sketchware.menu;

public final class DefaultExtraMenuBean {

    private DefaultExtraMenuBean() {
    }

    public static String getName(String menuName) {
        return switch (menuName) {
            case "image" -> "Custom Image";
            case "til_box_mode" -> "Box Mode";
            case "fabsize" -> "Fab Size";
            case "fabvisible" -> "Fab Visible";
            case "menuaction" -> "Menu Action";
            case "porterduff" -> "Porterduff Mode";
            case "transcriptmode" -> "Transcript Mode";
            case "listscrollparam", "recyclerscrollparam", "pagerscrollparam" -> "Scroll Param";
            case "gridstretchmode" -> "Stretch Mode";
            case "gravity_v" -> "Gravity Vertical";
            case "gravity_h" -> "Gravity Horizontal";
            case "gravity_t" -> "Gravity Toast";
            case "patternviewmode" -> "Pattern Mode";
            case "styleprogress" -> "Progress Style";
            case "cv_theme" -> "Theme";
            case "cv_language" -> "Language";
            case "import" -> "Import";
            default -> menuName;
        };
    }
}
