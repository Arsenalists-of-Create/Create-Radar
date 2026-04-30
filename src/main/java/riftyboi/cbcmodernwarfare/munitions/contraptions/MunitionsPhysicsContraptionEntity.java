package riftyboi.cbcmodernwarfare.munitions.contraptions;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.SynchedEntityData;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
public class MunitionsPhysicsContraptionEntity extends OrientedContraptionEntity {
    public MunitionsPhysicsContraptionEntity(net.minecraft.world.entity.EntityType<?> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); }
    public com.simibubi.create.content.contraptions.Contraption getContraption() { return null; }
    public void setContraptionMotion(Vec3 motion) {}
}
