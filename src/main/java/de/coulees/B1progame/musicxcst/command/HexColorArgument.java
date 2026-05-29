package de.coulees.B1progame.musicxcst.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

public final class HexColorArgument implements ArgumentType<String> {
    private static final Collection<String> EXAMPLES = List.of("#4d1111", "4d1111", "#00AAFF");
    private static final SimpleCommandExceptionType INVALID_HEX_COLOR = new SimpleCommandExceptionType(
            Component.literal("Expected hex color like #4d1111 or 4d1111")
    );

    private HexColorArgument() {
    }

    public static HexColorArgument hexColor() {
        return new HexColorArgument();
    }

    public static String getHexColor(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
        }

        int digitsStart = reader.getCursor();
        while (reader.canRead() && isHexDigit(reader.peek())) {
            reader.skip();
        }

        int digits = reader.getCursor() - digitsStart;
        if (digits != 6) {
            reader.setCursor(start);
            throw INVALID_HEX_COLOR.createWithContext(reader);
        }

        if (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.setCursor(start);
            throw INVALID_HEX_COLOR.createWithContext(reader);
        }

        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static boolean isHexDigit(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }
}
