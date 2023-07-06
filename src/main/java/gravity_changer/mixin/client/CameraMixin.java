package gravity_changer.mixin.client;

import java.util.Optional;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import gravity_changer.RotationAnimation;
import gravity_changer.api.GravityChangerAPI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Mixin(value = Camera.class, priority = 1001)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);
    
    @Shadow
    private Entity entity;
    
    @Shadow
    @Final
    private Quaternionf rotation;
    
    @Shadow
    private float eyeHeightOld;
    
    @Shadow
    private float eyeHeight;
    
    @WrapOperation(
        method = "setup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(DDD)V",
            ordinal = 0
        )
    )
    private void wrapOperation_update_setPos_0(
        Camera camera, double x, double y, double z,
        Operation<Void> original, BlockGetter area, Entity focusedEntity,
        boolean thirdPerson, boolean inverseView, float tickDelta
    ) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(focusedEntity); ;
        RotationAnimation animation = GravityChangerAPI.getRotationAnimation(focusedEntity);
        if (animation == null || (gravityDirection == Direction.DOWN && !animation.isInAnimation())) {
            original.call(this, x, y, z);
            return;
        }
        float partialTick = Minecraft.getInstance().getFrameTime();
        long timeMs = focusedEntity.level().getGameTime() * 50 + (long) (partialTick * 50);
        Quaternionf gravityRotation = new Quaternionf(animation.getCurrentGravityRotation(gravityDirection, timeMs));
        gravityRotation.conjugate();
        
        double entityX = Mth.lerp((double) tickDelta, focusedEntity.xo, focusedEntity.getX());
        double entityY = Mth.lerp((double) tickDelta, focusedEntity.yo, focusedEntity.getY());
        double entityZ = Mth.lerp((double) tickDelta, focusedEntity.zo, focusedEntity.getZ());
        
        double currentCameraY = Mth.lerp(tickDelta, this.eyeHeightOld, this.eyeHeight);
        
        Vector3f eyeOffset = new Vector3f(0, (float) currentCameraY, 0);
        eyeOffset.rotate(gravityRotation);
        
        original.call(
            this,
            entityX + eyeOffset.x(),
            entityY + eyeOffset.y(),
            entityZ + eyeOffset.z()
        );
    }
    
    @Inject(
        method = "Lnet/minecraft/client/Camera;setRotation(FF)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;",
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void inject_setRotation(CallbackInfo ci) {
        if (this.entity != null) {
            Direction gravityDirection = GravityChangerAPI.getGravityDirection(this.entity);
            RotationAnimation animation = GravityChangerAPI.getRotationAnimation(entity);
            if (animation == null) {
                return;
            }
            if (gravityDirection == Direction.DOWN && !animation.isInAnimation()) {
                return;
            }
            float partialTick = Minecraft.getInstance().getFrameTime();
            long timeMs = entity.level().getGameTime() * 50 + (long) (partialTick * 50);
            Quaternionf rotation = new Quaternionf(animation.getCurrentGravityRotation(gravityDirection, timeMs));
            rotation.conjugate();
            rotation.mul(this.rotation);
            this.rotation.set(rotation.x(), rotation.y(), rotation.z(), rotation.w());
        }
    }
}
