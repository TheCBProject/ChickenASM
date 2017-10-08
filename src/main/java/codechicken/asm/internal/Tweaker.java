package codechicken.asm.internal;

import codechicken.asm.ClassHierarchyManager;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

/**
 * As ChickenASM is just an ASM library, We register ourselves as a tweaker.
 * We only need to add transformer exclusions and a sniffer class transformer.
 * No actual class transformations happen from this library.
 *
 * Created by covers1624 on 8/10/2017.
 */
public class Tweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        classLoader.addTransformerExclusion("codechicken.asm.");
        classLoader.registerTransformer("codechicken.asm.internal.SnifferTransformer");
        Object obj = Launch.blackboard.get("fml.deobfuscatedEnvironment");
        System.out.println(obj);
    }

    @Override
    public String getLaunchTarget() {
        return "";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

}
