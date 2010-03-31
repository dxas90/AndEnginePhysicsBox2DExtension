package org.anddev.andengine.physics.box2d;

import java.util.ArrayList;

import org.anddev.andengine.entity.DynamicEntity;
import org.anddev.andengine.entity.StaticEntity;
import org.anddev.andengine.physics.DynamicPhysicsBody;
import org.anddev.andengine.physics.IPhysicsSpace;
import org.anddev.andengine.physics.StaticPhysicsBody;
import org.anddev.andengine.physics.box2d.util.BidirectionalMap;
import org.anddev.andengine.physics.box2d.util.Box2DJNIProxyContactListener;
import org.anddev.andengine.util.MathUtils;

import android.os.Handler;
import android.os.Message;



/**
 * @author Nicolas Gramlich
 * @since 10:56:23 - 21.03.2010
 */
public class Box2DPhysicsSpace implements IPhysicsSpace, Box2DContactListener {
	// ===========================================================
	// Constants
	// ===========================================================

	//	private static final float STEPTIME = 1 / 60f;

	private static final int ITERATIONS = 8;

	private static final int WHAT_CONTACT_NEW = 1;

	// ===========================================================
	// Fields
	// ===========================================================

	private final Box2DNativeWrapper mBox2DNativeWrapper;

	private final BidirectionalMap<StaticPhysicsBody, Integer> mStaticPhysicsBodyToPhysicsIDMapping = new BidirectionalMap<StaticPhysicsBody, Integer>();

	private final ArrayList<StaticPhysicsBody> mStaticPhysicsBodiesPendingToGoNative = new ArrayList<StaticPhysicsBody>();
	private final ArrayList<DynamicPhysicsBody> mDynamicPhysicsBodiesPendingToGoNative = new ArrayList<DynamicPhysicsBody>();

	private final ArrayList<DynamicPhysicsBody> mDynamicPhysicsBodies = new ArrayList<DynamicPhysicsBody>();
	private final BidirectionalMap<DynamicPhysicsBody, Integer> mDynamicPhysicsBodyToPhysicsIDMapping = new BidirectionalMap<DynamicPhysicsBody, Integer>();

	private final Box2DBodyInfo mBodyInfo = new Box2DBodyInfo();

	private Handler mHandler;

	// ===========================================================
	// Constructors
	// ===========================================================

	public Box2DPhysicsSpace() {
		this.mBox2DNativeWrapper = new Box2DNativeWrapper();
		this.mHandler = new Handler(){
			@Override
			public void handleMessage(Message pMsg) {
				if(pMsg.what == WHAT_CONTACT_NEW) {
					onHandleCollision(pMsg.arg1, pMsg.arg2);
				}
				super.handleMessage(pMsg);
			}
		};
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public void createWorld(final float pX, final float pY, final float pWidth, final float pHeight) {
		this.mBox2DNativeWrapper.createWorld(pX, pY, pX + pWidth, pY + pHeight, 0, 0);
	}

	@Override
	public void addDynamicEntity(final DynamicPhysicsBody pDynamicPhysicsBody) {
		assert(pDynamicPhysicsBody.mMass != 0);

		this.mDynamicPhysicsBodiesPendingToGoNative.add(pDynamicPhysicsBody);
	}
	
	@Override
	public void addStaticEntity(final StaticPhysicsBody pStaticPhysicsBody) {
		assert(pStaticPhysicsBody.mMass == 0);

		this.mStaticPhysicsBodiesPendingToGoNative.add(pStaticPhysicsBody);
	}

	@Override
	public void onNewContact(final int pPhysicsIDA, final int pPhysicsIDB) {
		this.mHandler.sendMessage(this.mHandler.obtainMessage(WHAT_CONTACT_NEW, pPhysicsIDA, pPhysicsIDB));
	}

	@Override
	public void setVelocity(final DynamicPhysicsBody pDynamicPhysicsBody, final float pVelocityX, final float pVelocityY) {
		final int physicsID = this.mDynamicPhysicsBodyToPhysicsIDMapping.get(pDynamicPhysicsBody);
		this.mBox2DNativeWrapper.setBodyLinearVelocity(physicsID, pVelocityX, pVelocityY);
	}

	@Override
	public void onUpdate(final float pSecondsElapsed) {
		this.loadPendingBodiesToGoNative();

		final Box2DNativeWrapper box2DNativeWrapper = this.mBox2DNativeWrapper;
		box2DNativeWrapper.step(new Box2DJNIProxyContactListener(this), pSecondsElapsed, ITERATIONS);

		final BidirectionalMap<DynamicPhysicsBody, Integer> dynamicPhysicsBodyToPhysicsIDMapping = this.mDynamicPhysicsBodyToPhysicsIDMapping;
		final ArrayList<DynamicPhysicsBody> dynamicPhysicsBodies = this.mDynamicPhysicsBodies;
		final Box2DBodyInfo bodyInfo = this.mBodyInfo;

		for(int i = dynamicPhysicsBodies.size() - 1; i >= 0; i--) {
			final DynamicPhysicsBody dynamicPhysicsBody = dynamicPhysicsBodies.get(i);
			box2DNativeWrapper.getBodyInfo(bodyInfo, dynamicPhysicsBodyToPhysicsIDMapping.get(dynamicPhysicsBody));
			final DynamicEntity dynamicEntity = dynamicPhysicsBody.getEntity();

			dynamicEntity.setPosition(bodyInfo.getX() - dynamicEntity.getInitialWidth() / 2, bodyInfo.getY() - dynamicEntity.getInitialHeight() / 2);
			dynamicEntity.setAngle(MathUtils.radToDeg(bodyInfo.getAngle()));
			dynamicEntity.setVelocity(bodyInfo.getVelocityX(), bodyInfo.getVelocityY());
		}
	}

	@Override
	public void setGravity(final float pGravityX, final float pGravityY) {
		this.mBox2DNativeWrapper.setGravity(pGravityX, pGravityY);
	}

	// ===========================================================
	// Methods
	// ===========================================================

	private void onHandleCollision(final int pPhysicsIDA, final int pPhysicsIDB) {
		final DynamicPhysicsBody dynamicPhysicsBodyA = this.mDynamicPhysicsBodyToPhysicsIDMapping.getKeyByValue(pPhysicsIDA);
		final DynamicPhysicsBody dynamicPhysicsBodyB = this.mDynamicPhysicsBodyToPhysicsIDMapping.getKeyByValue(pPhysicsIDB);
		
		if(dynamicPhysicsBodyA != null && dynamicPhysicsBodyB != null){
			if(dynamicPhysicsBodyA.hasCollisionCallback()){
				dynamicPhysicsBodyA.getCollisionCallback().onCollision(dynamicPhysicsBodyA.getEntity(), dynamicPhysicsBodyB.getEntity());
			}
			
			if(dynamicPhysicsBodyB.hasCollisionCallback()){
				dynamicPhysicsBodyB.getCollisionCallback().onCollision(dynamicPhysicsBodyB.getEntity(), dynamicPhysicsBodyA.getEntity());
			}
		} else if(dynamicPhysicsBodyA != null) {
			if(dynamicPhysicsBodyA.hasCollisionCallback()){
				final StaticPhysicsBody staticPhysicsBodyB = this.mStaticPhysicsBodyToPhysicsIDMapping.getKeyByValue(pPhysicsIDB);
				dynamicPhysicsBodyA.getCollisionCallback().onCollision(dynamicPhysicsBodyA.getEntity(), staticPhysicsBodyB.getEntity());
			}
		} else if(dynamicPhysicsBodyB != null) {
			if(dynamicPhysicsBodyB.hasCollisionCallback()){
				final StaticPhysicsBody staticPhysicsBodyA = this.mStaticPhysicsBodyToPhysicsIDMapping.getKeyByValue(pPhysicsIDA);
				dynamicPhysicsBodyB.getCollisionCallback().onCollision(dynamicPhysicsBodyB.getEntity(), staticPhysicsBodyA.getEntity());
			}
		}
	}

	private void loadPendingBodiesToGoNative() {
		// TODO Refactor the naming of all subcalls!
		this.loadPendingStaticBodiesToGoNative();
		this.loadPendingDynamicBodiesToGoNative();
	}

	private void loadPendingStaticBodiesToGoNative() {
		final ArrayList<StaticPhysicsBody> staticPhysicsBodiesPendingToGoNative = this.mStaticPhysicsBodiesPendingToGoNative;
		for(int i = staticPhysicsBodiesPendingToGoNative.size() - 1; i >= 0; i--) {
			loadPendingStaticBodyToGoNative(staticPhysicsBodiesPendingToGoNative.get(i));
		}
		staticPhysicsBodiesPendingToGoNative.clear();	
	}

	private void loadPendingStaticBodyToGoNative(final StaticPhysicsBody pStaticPhysicsBody) {
		final StaticEntity staticEntity = pStaticPhysicsBody.getEntity();
		
		final int physicsID = loadStaticShape(pStaticPhysicsBody, staticEntity);
		this.mStaticPhysicsBodyToPhysicsIDMapping.put(pStaticPhysicsBody, physicsID);
	}

	private int loadStaticShape(final StaticPhysicsBody pStaticPhysicsBody, final StaticEntity pStaticEntity) {
		final int physicsID;
		switch(pStaticPhysicsBody.mPhysicsShape) {
			case CIRCLE:
				physicsID = loadStaticCircle(pStaticPhysicsBody, pStaticEntity);
				break;
			case RECTANGLE:
			default:
				physicsID = loadStaticBox(pStaticPhysicsBody, pStaticEntity);
		}
		return physicsID;
	}

	private int loadStaticCircle(final StaticPhysicsBody pStaticPhysicsBody, final StaticEntity pStaticEntity) {		
		final float width = pStaticEntity.getInitialWidth();
		final float height = pStaticEntity.getInitialWidth();

		assert(width == height);

		final float x = pStaticEntity.getX() + width / 2;
		final float y = pStaticEntity.getY() + height / 2;
		final float radius = width / 2;

		return this.mBox2DNativeWrapper.createCircle(x, y, radius, 0, pStaticPhysicsBody.mElasticity, pStaticPhysicsBody.mFricition, false, false);
	}

	private int loadStaticBox(final StaticPhysicsBody pStaticPhysicsBody, final StaticEntity pStaticEntity) {
		final float width = pStaticEntity.getInitialWidth();
		final float height = pStaticEntity.getInitialHeight();
		final float x = pStaticEntity.getX() + width / 2;
		final float y = pStaticEntity.getY() + height / 2;
		
		return this.mBox2DNativeWrapper.createBox(x, y, width, height, 0, pStaticPhysicsBody.mElasticity, pStaticPhysicsBody.mFricition, false, false);
	}
	
	

	private void loadPendingDynamicBodiesToGoNative() {
		final ArrayList<DynamicPhysicsBody> dynamicPhysicsBodiesPendingToGoNative = this.mDynamicPhysicsBodiesPendingToGoNative;
		for(int i = dynamicPhysicsBodiesPendingToGoNative.size() - 1; i >= 0; i--) {
			loadPendingDynamicBodyToGoNative(dynamicPhysicsBodiesPendingToGoNative.get(i));
		}
		dynamicPhysicsBodiesPendingToGoNative.clear();
	}

	private void loadPendingDynamicBodyToGoNative(final DynamicPhysicsBody pDynamicPhysicsBody) {
		final DynamicEntity dynamicEntity = pDynamicPhysicsBody.getEntity();
		dynamicEntity.setUpdatePhysicsSelf(false);
		
		final int physicsID = loadDynamicShape(pDynamicPhysicsBody, dynamicEntity);
		this.mDynamicPhysicsBodies.add(pDynamicPhysicsBody);
		this.mDynamicPhysicsBodyToPhysicsIDMapping.put(pDynamicPhysicsBody, physicsID);
	}

	private int loadDynamicShape(final DynamicPhysicsBody pDynamicPhysicsBody, final DynamicEntity pDynamicEntity) {
		final int physicsID;
		switch(pDynamicPhysicsBody.mPhysicsShape) {
			case CIRCLE:
				physicsID = loadDynamicCircle(pDynamicPhysicsBody, pDynamicEntity);
				break;
			case RECTANGLE:
			default:
				physicsID = loadDynamicBox(pDynamicPhysicsBody, pDynamicEntity);
		}
		return physicsID;
	}

	private int loadDynamicCircle(final DynamicPhysicsBody pDynamicPhysicsBody, final DynamicEntity pDynamicEntity) {
		final float width = pDynamicEntity.getInitialWidth();
		final float height = pDynamicEntity.getInitialWidth();
		
		assert(width == height);
		
		final float x = pDynamicEntity.getX() + width / 2;
		final float y = pDynamicEntity.getY() + height / 2;
		final float radius = width / 2;
		
		return this.mBox2DNativeWrapper.createCircle(x, y, radius, pDynamicPhysicsBody.mMass, pDynamicPhysicsBody.mElasticity, pDynamicPhysicsBody.mFricition, pDynamicPhysicsBody.isFixedRotation(), pDynamicPhysicsBody.hasCollisionCallback());
	}

	private int loadDynamicBox(final DynamicPhysicsBody pDynamicPhysicsBody, final DynamicEntity pDynamicEntity) {
		final float width = pDynamicEntity.getInitialWidth();
		final float height = pDynamicEntity.getInitialHeight();
		final float x = pDynamicEntity.getX() + width / 2;
		final float y = pDynamicEntity.getY() + height / 2;
		
		return this.mBox2DNativeWrapper.createBox(x, y, width, height, pDynamicPhysicsBody.mMass, pDynamicPhysicsBody.mElasticity, pDynamicPhysicsBody.mFricition, pDynamicPhysicsBody.isFixedRotation(), pDynamicPhysicsBody.hasCollisionCallback());
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
