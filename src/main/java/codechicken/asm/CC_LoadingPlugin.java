package codechicken.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

import javax.annotation.Nullable;
import java.util.Map;

@TransformerExclusions("codechicken.asm")
@Name("CodeChickenLib ASM Library")
@MCVersion("1.12")
public class CC_LoadingPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "codechicken.asm.ClassHierarchyManager" };
    }

    @Override
    public String getModContainerClass() {
        return "";
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return "";
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return "";
    }
}
