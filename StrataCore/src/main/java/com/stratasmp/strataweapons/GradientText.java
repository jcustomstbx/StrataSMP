package com.stratasmp.strataweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GradientText {
   public static Component build(String text, TextColor from, TextColor to) {
      Component result = Component.empty();
      int length = text.length();

      for (int i = 0; i < length; i++) {
         float ratio = length <= 1 ? 0.0F : (float)i / (length - 1);
         TextColor color = interpolate(from, to, ratio);
         result = result.append(
            ((TextComponent)Component.text(String.valueOf(text.charAt(i)), color).decoration(TextDecoration.BOLD, true))
               .decoration(TextDecoration.ITALIC, false)
         );
      }

      return result;
   }

   private static TextColor interpolate(TextColor from, TextColor to, float ratio) {
      int r = Math.round(from.red() + (to.red() - from.red()) * ratio);
      int g = Math.round(from.green() + (to.green() - from.green()) * ratio);
      int b = Math.round(from.blue() + (to.blue() - from.blue()) * ratio);
      return TextColor.color(r, g, b);
   }
}
