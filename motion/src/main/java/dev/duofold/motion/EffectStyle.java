package dev.duofold.motion;

/** Persist stable IDs rather than UI positions so upgrades keep the selected effect. */
public enum EffectStyle {
    CLASSIC("classic", 0, "经典磨砂 · 默认", "保留当前效果。倾斜越大、远离固定边缘越远，模糊和暗部越明显；停住时保持磨砂。"),
    EDGE("edge", 1, "边缘渐隐", "固定边缘保持清楚，模糊与暗部逐渐集中到展开的一侧；停住时保持渐变。"),
    SOFT("soft", 2, "柔和景深", "使用更柔和的模糊与较轻的暗部，停住时保留轻盈的玻璃景深。"),
    SETTLE("settle", 3, "悬停清晰", "移动时出现模糊，停住约半秒后逐渐清晰；透视位置保持不动，再次移动恢复模糊。"),
    CLEAR("clear", 4, "清透投影", "保留翻折透视，关闭磨砂模糊与额外暗部，移动和停住时都保持清晰。"),
    BOKEH("bokeh", 5, "镜头散景", "独立镜头透视，近边清晰、远边逐渐化开；停住时保持景深。空间强度控制光圈，100% 为完整散景。");

    public final String id, title, description;
    public final int shaderId;
    EffectStyle(String id,int shaderId,String title,String description) {
        this.id=id;this.shaderId=shaderId;this.title=title;this.description=description;
    }
    public static EffectStyle fromId(String id) {
        for(EffectStyle style:values())if(style.id.equals(id))return style;
        return CLASSIC;
    }
}
