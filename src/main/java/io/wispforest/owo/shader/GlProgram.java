package io.wispforest.owo.shader;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wispforest.owo.mixin.shader.ShaderInstanceAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.io.ResourceProvider;
import net.minecraft.util.Tuple;

/**
 * A simple wrapper around Minecraft's built-in core shaders. In order to load and use
 * a custom shader from your resources, place it in {@code assets/<mod id>/shaders/core/}.
 * You can look up the required files and their format on <a href="https://minecraft.fandom.com/wiki/Shaders">the Minecraft Wiki</a>
 * <p>
 * This wrapper fully supports custom uniforms. If you require any, extend this class, then grab the
 * uniforms via {@link #findUniform(String)} inside your {@link #setup()} override and store them in
 * fields. This method gets executed once the actual shader program has been compiled and
 * linked, ready for use. Look at {@link BlurProgram} for reference
 * <p>
 * GlPrograms automatically register themselves for loading in the constructor - as such,
 * some caution on when and where the constructor is invoked is advisable. Ideally, store
 * and initialize programs in static fields of your client initializer
 */
public class GlProgram {

    private static final List<Tuple<Function<ResourceProvider, ShaderInstance>, Consumer<ShaderInstance>>> REGISTERED_PROGRAMS = new ArrayList<>();

    /**
     * The actual Minecraft shader program
     * which is represented and wrapped by this
     * GlProgram instance
     */
    protected ShaderInstance backingProgram;

    public GlProgram(Identifier id, VertexFormat vertexFormat) {
        REGISTERED_PROGRAMS.add(new Tuple<>(
                resourceFactory -> {
                    try {
                        return new OwoShaderProgram(resourceFactory, id.toString(), vertexFormat);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialized owo shader program", e);
                    }
                },
                program -> {
                    this.backingProgram = program;
                    this.setup();
                }
        ));
    }

    /**
     * Bind this program and execute
     * potential preparation work
     * <p>
     * <b>Note:</b> Custom implementations may very well have
     * additional setup methods that must be run prior to
     * invoking {@code use()}
     */
    public void use() {
        RenderSystem.setShader(() -> this.backingProgram);
    }

    protected void setup() {}

    /**
     * Get the {@link Uniform} generated by the game for
     * the uniform of the given name
     *
     * @return The corresponding {@link Uniform} instance for updating
     * the value of the uniform, or {@code null} if no such uniform exists
     */
    protected @Nullable Uniform findUniform(String name) {
        return ((ShaderInstanceAccessor) this.backingProgram).owo$getUniformMap().get(name);
    }

    @ApiStatus.Internal
    public static void forEachProgram(Consumer<Tuple<Function<ResourceProvider, ShaderInstance>, Consumer<ShaderInstance>>> loader) {
        REGISTERED_PROGRAMS.forEach(loader);
    }

    public static class OwoShaderProgram extends ShaderInstance {
        private OwoShaderProgram(ResourceProvider factory, String name, VertexFormat format) throws IOException {
            super(factory, name, format);
        }
    }
}
