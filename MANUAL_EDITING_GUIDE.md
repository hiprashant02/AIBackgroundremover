# Manual Brush Editing Feature - Complete Guide

## Overview
The Manual Brush Editing feature allows users to refine AI-generated background removal by manually painting areas to keep or remove. This gives users precise control over the final result.

## Features

### 1. **Brush Modes**

#### Erase Mode (Red)
- **Purpose**: Remove background areas that the AI didn't catch
- **Use Case**: Remove leftover background fragments, unwanted objects
- **Indicator**: Red brush cursor and indicator

#### Restore Mode (Green)
- **Purpose**: Restore foreground areas that were incorrectly removed
- **Use Case**: Bring back important body parts, objects that should be kept
- **Indicator**: Green brush cursor and indicator

### 2. **Brush Controls**

#### Basic Settings:
- **Brush Size**: 10-200 pixels
  - Adjustable via slider
  - Auto-calculated optimal size based on image dimensions
  - Real-time preview while drawing

#### Advanced Settings (Toggle to reveal):
- **Brush Hardness**: 10-100%
  - Controls edge softness
  - Higher = sharper edges
  - Lower = softer, feathered edges

- **Brush Opacity**: 10-100%
  - Controls brush strength
  - Lower opacity = gradual changes
  - Higher opacity = immediate full effect

### 3. **User Interface**

#### Top Bar Actions:
```
[←] [Undo] [Redo] [Edit/✓] [Save]
```

- **Edit Button**: Toggle manual edit mode
- **Check Button** (in edit mode): Apply and exit edit mode
- **Disabled Save**: Save is disabled during editing

#### Bottom Panel (Edit Mode):
```
┌─────────────────────────────────────┐
│  Manual Edit            [Cancel] [Done]│
│                                       │
│  Brush Mode:                          │
│  [Erase]  [Restore]                   │
│                                       │
│  Brush Size: 50px                     │
│  ═════●═════════                      │
│                                       │
│  [Show Advanced] ▼                    │
│                                       │
│  [Clear] [Smooth]                     │
└─────────────────────────────────────┘
```

#### Mode Indicator:
- Floating badge in top-left corner
- Shows current mode (Erase/Restore)
- Color-coded (Red/Green)

### 4. **Additional Tools**

#### Clear Strokes
- Removes all manual edits
- Resets mask to original AI result
- Useful for starting over

#### Smooth Mask
- Applies edge smoothing algorithm
- Reduces jagged edges
- Improves overall quality
- Uses 3-pixel radius blur

## How to Use

### Step 1: Initial AI Processing
1. Select image from gallery/camera
2. Wait for AI to process
3. View result with transparent background

### Step 2: Enter Manual Edit Mode
1. Tap **Edit** button (pencil icon) in top bar
2. Bottom panel changes to brush controls
3. Image becomes interactive drawing canvas

### Step 3: Choose Brush Mode

#### To Remove Background (Erase):
```
1. Select "Erase" mode (red)
2. Adjust brush size if needed
3. Paint over areas to remove
4. Use lower opacity for gradual removal
```

#### To Restore Foreground (Restore):
```
1. Select "Restore" mode (green)
2. Adjust brush size if needed
3. Paint over areas to keep
4. Use softer brush for natural edges
```

### Step 4: Fine-Tune Settings

#### For Large Areas:
- Increase brush size (150-200px)
- Use higher opacity (80-100%)
- Use lower hardness (40-60%) for smooth edges

#### For Details:
- Decrease brush size (10-30px)
- Use higher hardness (80-100%)
- Use full opacity (100%)

#### For Soft Transitions:
- Medium brush size (50-100px)
- Lower opacity (30-50%)
- Low hardness (20-40%)

### Step 5: Apply Changes
1. Tap **Done** to apply edits
2. Or tap **Cancel** to discard changes
3. Result is added to undo stack

### Step 6: Post-Processing (Optional)
1. Use **Smooth** button to refine edges
2. Use **Clear** to start over if needed
3. Apply different backgrounds after editing

## Technical Details

### Drawing System

#### Path-Based Drawing:
```kotlin
DrawingPath {
    points: List<DrawingPoint>  // Touch coordinates
    brushTool: BrushTool        // Settings at time of stroke
}
```

#### Point Structure:
```kotlin
DrawingPoint {
    x: Float            // Normalized 0-1
    y: Float            // Normalized 0-1
    pressure: Float     // 0-1 (for future stylus support)
}
```

### Mask Processing

#### Mask Creation:
1. Extract alpha channel from AI result
2. Convert to grayscale mask
3. Store as editable bitmap

#### Brush Application:
1. Convert touch coordinates to bitmap space
2. Draw using Porter-Duff blending:
   - **Erase**: DST_OUT (removes alpha)
   - **Restore**: DST_OVER (adds alpha)
3. Apply gradient for soft edges
4. Connect points for smooth strokes

#### Edge Smoothing:
- Box blur on alpha channel
- 3-pixel radius by default
- Preserves detail while smoothing

### Performance Optimizations

1. **Normalized Coordinates**: Resolution-independent drawing
2. **Deferred Processing**: Real-time preview, batch processing on done
3. **Bitmap Reuse**: Minimal memory allocation
4. **Coroutine-Based**: Non-blocking UI updates

## Best Practices

### For Best Results:

#### 1. Start with Erase Mode
```
✓ Remove obvious background fragments first
✓ Use large brush for big areas
✓ Switch to smaller brush for details
```

#### 2. Then Use Restore Mode
```
✓ Bring back incorrectly removed parts
✓ Use soft brush for natural look
✓ Build up gradually with lower opacity
```

#### 3. Refine Edges
```
✓ Use small brush (10-20px)
✓ High hardness for sharp objects
✓ Low hardness for hair, fur
✓ Apply Smooth for final polish
```

#### 4. Check Different Backgrounds
```
✓ Test with white background
✓ Test with black background
✓ Verify transparency is correct
```

### Common Issues & Solutions

#### Issue: Jagged Edges
**Solution**: 
- Use lower hardness (30-50%)
- Apply Smooth mask
- Use multiple low-opacity strokes

#### Issue: Halos Around Subject
**Solution**:
- Use Erase mode
- Small brush (15-25px)
- Carefully trace edges
- Lower opacity (50-70%)

#### Issue: Lost Details
**Solution**:
- Use Restore mode
- Very small brush (10-15px)
- High hardness (90-100%)
- Paint precisely

#### Issue: Unnatural Look
**Solution**:
- Clear and start over
- Use softer brush (hardness 40-60%)
- Build up gradually with opacity
- Apply Smooth when done

## Keyboard Shortcuts (Future)
*Planned for future updates*
- **[** / **]**: Decrease/Increase brush size
- **E**: Switch to Erase mode
- **R**: Switch to Restore mode
- **Z**: Undo
- **Y**: Redo

## Advanced Tips

### 1. Hair and Fur
```
Settings:
- Size: 30-50px
- Hardness: 30-40%
- Opacity: 60-80%

Technique:
- Follow hair direction
- Multiple light strokes
- Use Restore for stray hairs
- Apply Smooth at end
```

### 2. Complex Backgrounds
```
Settings:
- Size: Varies by area
- Hardness: 60-80%
- Opacity: 100%

Technique:
- Remove large areas first
- Zoom in for details
- Switch modes frequently
- Save often to undo stack
```

### 3. Glass and Transparency
```
Settings:
- Size: 20-40px
- Hardness: 80-100%
- Opacity: 100%

Technique:
- Trace edges precisely
- Keep internal reflections
- Sharp, clean strokes
- No smoothing needed
```

## API Reference

### EditorViewModel Methods

```kotlin
// Enter manual editing mode
fun enterManualEditMode()

// Exit and optionally apply changes
fun exitManualEditMode(applyChanges: Boolean = true)

// Add a brush stroke
fun addBrushStroke(path: DrawingPath)

// Update brush settings
fun updateBrushTool(
    mode: BrushMode? = null,
    size: Float? = null,
    hardness: Float? = null,
    opacity: Float? = null
)

// Clear all manual edits
fun clearBrushStrokes()

// Smooth mask edges
fun smoothMask()
```

### ManualEditingProcessor Methods

```kotlin
// Apply multiple brush strokes to mask
suspend fun applyBrushStrokes(
    originalMask: Bitmap,
    paths: List<DrawingPath>,
    imageWidth: Int,
    imageHeight: Int
): Bitmap

// Create mask from foreground bitmap
suspend fun createMaskFromForeground(
    foregroundBitmap: Bitmap
): Bitmap

// Apply edited mask to original image
suspend fun applyEditedMask(
    originalBitmap: Bitmap,
    editedMask: Bitmap
): Bitmap

// Smooth mask edges
suspend fun smoothMask(
    mask: Bitmap,
    radius: Int = 2
): Bitmap

// Calculate optimal brush size
fun calculateOptimalBrushSize(
    imageWidth: Int,
    imageHeight: Int
): Float
```

## Examples

### Example 1: Remove Complex Background
```
1. AI removes 90% of background
2. Enter Edit Mode
3. Erase Mode, size: 100px
4. Remove large fragments
5. Size: 30px for details
6. Smooth mask
7. Done
```

### Example 2: Fix Hair Details
```
1. AI cuts off hair edges
2. Enter Edit Mode
3. Restore Mode
4. Size: 20px, Hardness: 30%
5. Trace hair strands
6. Multiple light strokes
7. Smooth mask
8. Done
```

### Example 3: Preserve Complex Object
```
1. AI removes part of object
2. Enter Edit Mode
3. Restore Mode
4. Size: 50px, Hardness: 80%
5. Paint over object area
6. Size: 15px for edges
7. Done
```

## Troubleshooting

### Performance Issues
- Close other apps
- Use smaller images (< 2048px)
- Clear strokes if too many
- Restart edit mode

### Unexpected Behavior
- Exit and re-enter edit mode
- Check brush mode indicator
- Verify opacity setting
- Try clearing strokes

### Quality Issues
- Increase brush hardness
- Use smaller brush size
- Apply smooth multiple times
- Consider AI reprocessing

## Future Enhancements

### Planned Features:
1. ✨ Pressure-sensitive drawing (stylus support)
2. ✨ Pinch-to-zoom for precise editing
3. ✨ Brush presets (hair, skin, object)
4. ✨ Layer system for non-destructive editing
5. ✨ Magic wand selection tool
6. ✨ Edge detection assist
7. ✨ Before/after comparison slider
8. ✨ Tutorial mode with examples

## Conclusion

The Manual Brush Editing feature gives you complete control over background removal. Combined with AI processing, you can achieve professional-quality results for any image.

**Key Takeaways:**
- ✓ Use AI first, refine manually
- ✓ Erase removes, Restore adds
- ✓ Adjust brush for different areas
- ✓ Smooth for final polish
- ✓ Practice makes perfect!

Happy editing! 🎨

