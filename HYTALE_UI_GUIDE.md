# Hytale Custom UI Creation Guide

## Overview
This guide documents the syntax rules and requirements for creating Custom UI in Hytale plugins, learned through extensive trial-and-error debugging of actual UI parse errors.

## Architecture

### File Structure
- **UI Markup Files**: `.ui` files must be placed in `src/main/resources/Common/UI/Custom/`
- **Java Handler Classes**: Java classes that bind to UI events go in your plugin's package structure
- **Manifest Requirement**: Your `manifest.json` must include `"IncludesAssetPack": true`

### UI Page Types
```java
// Use InteractiveCustomUIPage with typed event data
public class MyPage extends InteractiveCustomUIPage<MyPage.Data> {
    
    public record Data(
        @CodecKey("buttonAction") String action,
        @CodecKey("closeAction") String close
    ) implements BuilderCodec<Data> {}
    
    @Override
    public BuiltUIDocument build() {
        return BuiltUIDocument.create("MyPage.ui", Data.codec());
    }
}
```

## Syntax Rules (CRITICAL)

### Property Syntax
```
✅ CORRECT: Property: (Key: Value, Key2: Value2);
❌ WRONG:   Property: {Key: Value; Key2: Value2;}
```
- Use **parentheses ()** for property values, NOT curly braces {}
- Use **commas** to separate multiple key-value pairs within properties
- End property declarations with **semicolons**
- Do NOT use semicolons after closing braces

### Valid Element Properties

#### Group Properties
```
Group {
    LayoutMode: Center;           // Values: Top, Center, Bottom, Left, Right
    Anchor: (Width: 500, Height: 300);
    Padding: (Full: 30);          // Or: (Left: 10, Right: 10, Top: 5, Bottom: 5)
    Background: (Color: #AARRGGBB); // RGBA hex: AA = alpha (opacity)
}
```
**INVALID on Groups:**
- `Border` - Border property is NOT supported on Groups

#### Button Properties
```
Button #MyButton {
    Anchor: (Width: 200, Height: 45);
    Padding: (Left: 10, Right: 10);
    Background: (Color: #E62E7D32); // Semi-transparent green
    
    Label {
        Text: "Button Text";
        Style: (FontSize: 18, Alignment: Center);
    }
}
```
**IMPORTANT:**
- Buttons do NOT support `Text` property directly
- Buttons must contain a child `Label` element for text
- `Border` property is NOT supported on Buttons

#### Label Properties
```
Label #MyLabel {
    Text: "Display text here";
    Style: (FontSize: 20, Alignment: Center);
    Padding: (Bottom: 15);
}
```
**INVALID in Label Style:**
- `Color` - Color is NOT a valid field in LabelStyle (uses theme defaults)

### Color Format
```
Background: (Color: #AARRGGBB);
```
- **Format**: 8-digit hex with alpha channel first
- **Alpha Channel**: `FF` = fully opaque, `00` = fully transparent
  - `F2` ≈ 95% opacity
  - `E6` ≈ 90% opacity
  - `CC` ≈ 80% opacity
  - `80` ≈ 50% opacity

**WRONG APPROACHES:**
```
❌ Background: (Color: #RRGGBB, Opacity: 0.95);  // No separate Opacity field
❌ Background: {Color: #2A2A2E, Opacity: 0.95;}; // Wrong brackets
```

### Layout Modes
```
LayoutMode: Top;      // Vertical layout, top-aligned
LayoutMode: Center;   // Centered
LayoutMode: Bottom;   // Bottom-aligned
LayoutMode: Left;     // Horizontal layout, left-aligned
LayoutMode: Right;    // Right-aligned
```
**INVALID:**
- `Vertical` - Use `Top` instead
- `Horizontal` - Use `Left` instead

## Complete Working Example

### LockConfirmation.ui
```
Group {
    LayoutMode: Center;

    Group #MyPanel {
        LayoutMode: Top;
        Anchor: (Width: 500, Height: 300);
        Padding: (Full: 30);
        Background: (Color: #F22A2A2E);

        Label #Title {
            Text: "Lock Chest?";
            Style: (FontSize: 28, Alignment: Center);
            Padding: (Bottom: 15);
        }

        Label #Description {
            Text: "";
            Style: (FontSize: 16, Alignment: Center);
            Padding: (Bottom: 25);
        }

        Group {
            LayoutMode: Left;
            Anchor: (Height: 50);
            Padding: (Top: 10);

            Button #ConfirmButton {
                Anchor: (Width: 200, Height: 45);
                Padding: (Left: 10, Right: 5);
                Background: (Color: #E62E7D32);
                
                Label {
                    Text: "✓ Confirm Lock";
                    Style: (FontSize: 18, Alignment: Center);
                }
            }

            Button #CancelButton {
                Anchor: (Width: 200, Height: 45);
                Padding: (Left: 5, Right: 10);
                Background: (Color: #E6C62828);
                
                Label {
                    Text: "✗ Cancel";
                    Style: (FontSize: 18, Alignment: Center);
                }
            }
        }
    }
}
```

### LockConfirmationPage.java
```java
package me.ascheladd.hytale.neolocks.ui;

import com.hytale.game.data.codec.CodecKey;
import com.hytale.game.server.interfaces.ui.BuiltUIDocument;
import com.hytale.game.server.interfaces.ui.BuilderCodec;
import com.hytale.game.server.interfaces.ui.InteractiveCustomUIPage;

public class LockConfirmationPage extends InteractiveCustomUIPage<LockConfirmationPage.Data> {

    public record Data(
        @CodecKey("buttonAction") String action
    ) implements BuilderCodec<Data> {}

    @Override
    public BuiltUIDocument build() {
        return BuiltUIDocument.create("LockConfirmation.ui", Data.codec());
    }
}
```

### Event Binding in Listener
```java
import com.hytale.game.server.event.interaction.InteractionContext;
import com.hytale.game.server.interfaces.ui.event.CustomUIEventBindingType;
import com.hytale.game.server.interfaces.ui.event.EventData;

// Open the UI
InteractionContext context = event.getContext();
LockConfirmationPage page = new LockConfirmationPage();

page.setEvent(CustomUIEventBindingType.Activating, "#ConfirmButton", 
    EventData.of("@buttonAction", "confirm"));
page.setEvent(CustomUIEventBindingType.Activating, "#CancelButton", 
    EventData.of("@buttonAction", "cancel"));

context.sendCustomUI(page, (ctx, data) -> {
    if ("confirm".equals(data.action())) {
        // Handle confirm action
    } else if ("cancel".equals(data.action())) {
        // Handle cancel action
    }
});
```

## Common Errors and Solutions

### Error: "Expected expression, found {"
**Cause**: Using curly braces instead of parentheses for property values  
**Fix**: Change `Property: {Value}` to `Property: (Value)`

### Error: "Could not resolve expression for property LayoutMode"
**Cause**: Invalid enum value like "Vertical"  
**Fix**: Use valid values: `Top`, `Center`, `Bottom`, `Left`, `Right`

### Error: "Unknown property Text on node of type Button"
**Cause**: Trying to set Text directly on Button  
**Fix**: Add a child Label element with the text

### Error: "Could not find field Opacity in type PatchStyle"
**Cause**: Using separate Opacity field  
**Fix**: Use RGBA color format with alpha channel: `#AARRGGBB`

### Error: "Unknown property Border on node of type Group"
**Cause**: Border is not supported on Groups  
**Fix**: Remove Border property from Groups

### Error: "Unknown property Border on node of type Button"
**Cause**: Border is not supported on Buttons  
**Fix**: Remove Border property from Buttons

### Error: "Could not find field Color in type LabelStyle"
**Cause**: Color is not a valid field in label styles  
**Fix**: Remove Color from Style definitions, use default theme colors

## Best Practices

1. **Start Simple**: Create minimal UI first, test loading, then add styling
2. **One Property at a Time**: When debugging, add properties incrementally
3. **Test Frequently**: Run `mvn clean install` after each change
4. **Check Error Location**: Parse errors show exact file, line, and character position
5. **Use Element IDs**: Prefix with `#` for event binding (e.g., `#ConfirmButton`)
6. **Limit Styling**: Avoid unsupported properties - stick to known working ones
7. **Alpha Channel Opacity**: Calculate alpha as `floor(opacity * 255)` then convert to hex
   - 95% = 242 = 0xF2
   - 90% = 230 = 0xE6
   - 80% = 204 = 0xCC
   - 50% = 128 = 0x80

## Decompilation Tips

If you need to understand more about the UI system:
1. Decompile `HytaleServer.jar` using a Java decompiler (JD-GUI, Fernflower, etc.)
2. Look for classes in packages like:
   - `com.hytale.game.server.interfaces.ui.*`
   - `com.hytale.game.data.ui.*`
3. Search for `PatchStyle`, `LabelStyle`, `GroupStyle`, `ButtonStyle` to see valid properties
4. Check `InteractiveCustomUIPage` and `BuiltUIDocument` for API usage examples

## Property Reference (Known Working)

### Universal Properties
- `Anchor: (Width: <number>, Height: <number>)`
- `Padding: (Full: <number>)` or `(Left: <n>, Right: <n>, Top: <n>, Bottom: <n>)`
- `Background: (Color: #AARRGGBB)`

### Group-Specific
- `LayoutMode: <Top|Center|Bottom|Left|Right>`

### Label-Specific
- `Text: "<string>"`
- `Style: (FontSize: <number>, Alignment: <Left|Center|Right>)`

### Known INVALID Properties
- `Border` on any element type
- `Color` in Label Style
- `Opacity` as separate field
- `Text` directly on Button

## Troubleshooting Workflow

1. **Get exact error**: Check Hytale client logs for parse errors
2. **Locate error**: Note file name, line number, character position
3. **Identify property**: Determine which property is causing the issue
4. **Check this guide**: See if property is listed as invalid
5. **Remove/fix property**: Make the correction
6. **Rebuild**: Run `mvn clean install`
7. **Test**: Deploy JAR to server and restart
8. **Repeat**: Continue until no parse errors occur

## Version Info
- Hytale Server: 1.0-SNAPSHOT
- Java: 25
- Last Updated: January 16, 2026
- Document Source: Trial-and-error debugging of actual Hytale UI parse errors
