package io.github.bananapuncher714.cartographer.module.vanilla;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor.Type;
import org.bukkit.permissions.PermissionDefault;

import io.github.bananapuncher714.cartographer.core.Cartographer;
import io.github.bananapuncher714.cartographer.core.api.command.CommandBase;
import io.github.bananapuncher714.cartographer.core.api.command.CommandParameters;
import io.github.bananapuncher714.cartographer.core.api.command.SubCommand;
import io.github.bananapuncher714.cartographer.core.api.command.executor.CommandExecutableMessage;
import io.github.bananapuncher714.cartographer.core.api.command.validator.InputValidatorInt;
import io.github.bananapuncher714.cartographer.core.api.command.validator.sender.SenderValidatorPlayer;
import io.github.bananapuncher714.cartographer.core.api.permission.PermissionBuilder;
import io.github.bananapuncher714.cartographer.core.map.Minimap;
import io.github.bananapuncher714.cartographer.core.map.menu.MapMenu;
import io.github.bananapuncher714.cartographer.core.module.Module;
import io.github.bananapuncher714.cartographer.core.renderer.CartographerRenderer;
import io.github.bananapuncher714.cartographer.core.util.CrossVersionMaterial;
import io.github.bananapuncher714.cartographer.core.util.FailSafe;
import io.github.bananapuncher714.cartographer.core.util.FileUtil;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorConverter;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorConverterEntity;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorConverterNamedLocation;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorConverterPlayer;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorProviderDeathLocation;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorProviderEntity;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorProviderPlayer;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.CursorProviderSpawnLocation;
import io.github.bananapuncher714.cartographer.module.vanilla.providers.ObjectProvider;

public class VanillaPlus extends Module {
	private Map< UUID, Location > deaths = new HashMap< UUID, Location >();
	
	private boolean isBlacklist;
	private Set< String > blacklistedWorlds = new HashSet< String >();
	
	private Set< CursorConverter > defaultConverters = new HashSet< CursorConverter >();

	private Map< UUID, PlayerViewer > viewers = new HashMap< UUID, PlayerViewer >();
	private Map< EntityType, CrossVersionMaterial > entityMaterials = new HashMap< EntityType, CrossVersionMaterial >();
	
	private VanillaWorldCursorProvider cursorProvider = new VanillaWorldCursorProvider( this );
	
	@Override
	public void onEnable() {
		registerListener( new VanillaListener( this ) );
		
		for ( Minimap minimap : getCartographer().getMapManager().getMinimaps().values() ) {
			minimap.registerProvider( cursorProvider );
		}
		
		registerCommand( new CommandBase( "test" )
				.setSubCommand( new SubCommand( "test" )
						.add( new SubCommand( new InputValidatorInt( 0, 0xFFFFFF ) )
								.addSenderValidator( new SenderValidatorPlayer() )
								.defaultTo( this::showMenu ) )
						.whenUnknown( new CommandExecutableMessage( ChatColor.RED + "You must provide a color!" ) )
						.defaultTo( new CommandExecutableMessage( ChatColor.RED + "You must provide an argument!" ) ) )
				.setDescription( "Test command" )
				.setPermission( new PermissionBuilder( "test" ).setDefault( PermissionDefault.OP ).register().build() )
				.build() );
		
		FileUtil.saveToFile( getResource( "README.md" ), new File( getDataFolder() + "/README.md" ), true );
		FileUtil.saveToFile( getResource( "config.yml" ), new File( getDataFolder() + "/config.yml" ), false );
		
		loadConfig();
	}
	
	private void showMenu( CommandSender sender, String[] args, CommandParameters parameters ) {
		Player player = ( Player ) sender;
		int color = parameters.getLast( int.class );

		ItemStack item = Cartographer.getUtil().getMainHandItem( player );
		if ( item == null || !Cartographer.getInstance().getMapManager().isMinimapItem( item ) ) {
			sender.sendMessage( ChatColor.RED + "You must be holding a minimap!" );
			return;
		}
		CartographerRenderer renderer = Cartographer.getInstance().getMapManager().getRendererFrom( Cartographer.getUtil().getMapViewFrom( item ) );
		
		MapMenu menu = new MapMenu();
		menu.addComponent( new MenuComponentSolid( color ) );
		
		renderer.setMapMenu( player.getUniqueId(), menu );
	}
	
	@Override
	public void onDisable() {
	}
	
	private void loadConfig() {
		FileConfiguration config = YamlConfiguration.loadConfiguration( new File( getDataFolder() + "/" + "config.yml" ) );
		isBlacklist = config.getBoolean( "blacklist", true );
		blacklistedWorlds.addAll( config.getStringList( "blacklisted-worlds" ) );
		
		if ( config.contains( "cursors" ) ) {
			ConfigurationSection cursorSection = config.getConfigurationSection( "cursors" );
			for ( String key : cursorSection.getKeys( false ) ) {
				if ( !cursorSection.getBoolean( key + ".enabled" ) ) continue;
				
				ObjectProvider< NamedLocation > provider = null;
				if ( key.equalsIgnoreCase( "spawn" ) ) {
					provider = new CursorProviderSpawnLocation();
				} else if ( key.equalsIgnoreCase( "death" ) ) {
					provider = new CursorProviderDeathLocation( this );
				}
				
				if ( provider != null ) {
					String iconTypes = cursorSection.getString( key + ".icon" );
					Type type = FailSafe.getEnum( Type.class, iconTypes.split( "\\s+" ) );
					CursorVisibility visibility = FailSafe.getEnum( CursorVisibility.class, cursorSection.getString( key + ".default-visibility" ) );
					boolean showName = cursorSection.getBoolean( key + ".show-name" );
					String displayName = ChatColor.translateAlternateColorCodes( '&', cursorSection.getString( key + ".display-name" ) );
					
					CursorConverterNamedLocation converter = new CursorConverterNamedLocation( this, key );
					converter.setType( type );
					converter.setShowName( showName );
					converter.setDisplayName( displayName );
					converter.setVisibility( visibility );
					defaultConverters.add( converter );
				}
			}
		}
		
		if ( config.contains( "players" ) ) {
			String iconTypes = config.getString( "players.icon" );
			Type type = FailSafe.getEnum( Type.class, iconTypes.split( "\\s+" ) );
			CursorVisibility visibility = FailSafe.getEnum( CursorVisibility.class, config.getString( "players.default-visibility" ) );
			boolean showName = config.getBoolean( "players.show-name" );
			double range = config.getDouble( "players.range" );
			
			CursorConverterPlayer converter = new CursorConverterPlayer( null );
			converter.setShowName( showName );
			converter.setIcon( type );
			converter.setVisibility( visibility );
			defaultConverters.add( converter );
			
			CursorProviderPlayer provider = new CursorProviderPlayer( range );
			
			cursorProvider.setPlayerProvider( provider );
		}
		
		if ( config.contains( "entity" ) ) {
			ConfigurationSection section = config.getConfigurationSection( "entity" );
			for ( String key : section.getKeys( false ) ) {
				EntityType type = EntityType.valueOf( key.toUpperCase() );
				String iconTypes = section.getString( key + ".icon" );
				Type icon = FailSafe.getEnum( Type.class, iconTypes.split( "\\s+" ) );
				double range = section.getDouble( key + ".range" );
				CursorVisibility visibility = FailSafe.getEnum( CursorVisibility.class, section.getString( key + ".default-visibility" ) );
				boolean showName = section.getBoolean( key + ".show-name" );
				String materialSer = section.getString( key + ".item" );
				
				String[] matVals = materialSer.split( ":" );
				Material material = Material.getMaterial( matVals[ 0 ].toUpperCase() );
				int durability = matVals.length > 1 ? Integer.parseInt( matVals[ 1 ] ): 0;
				if ( material == null ) {
					material = Material.SPAWNER;
				}
				CrossVersionMaterial cvMaterial = new CrossVersionMaterial( material, durability );
				entityMaterials.put( type, cvMaterial );

				CursorConverterEntity converter = new CursorConverterEntity( type );
				converter.setIcon( icon );
				converter.setVisibility( visibility );
				converter.setShowName( showName );
				defaultConverters.add( converter );
				
				CursorProviderEntity provider = new CursorProviderEntity( type, range );
				cursorProvider.addEntityProvider( provider );
			}
		}
	}
	
	
	
	public Location getDeathOf( UUID uuid ) {
		return deaths.get( uuid );
	}
	
	public void setDeathOf( UUID uuid, Location loc ) {
		if ( loc == null ) {
			deaths.remove( uuid );
		} else {
			deaths.put( uuid, loc.clone() );
		}
	}
	
	public boolean isWhitelisted( World world ) {
		return isBlacklist ^ blacklistedWorlds.contains( world.getName() );
	}
	
	public CursorConverter getConverterFor( Object object ) {
		for ( CursorConverter converter : defaultConverters ) {
			if ( converter.convertable( object ) ) {
				return converter;
			}
		}
		return null;
	}
	
	public PlayerViewer getViewerFor( UUID uuid ) {
		PlayerViewer viewer = viewers.get( uuid );
		if ( viewer == null ) {
			viewer = new PlayerViewer( this );
			viewers.put( uuid, viewer );
		}
		return viewer;
	}
	
	public VanillaWorldCursorProvider getCursorProvider() {
		return cursorProvider;
	}
}
