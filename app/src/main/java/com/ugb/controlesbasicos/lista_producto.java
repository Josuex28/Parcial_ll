package com.ugb.controlesbasicos;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

public class lista_producto extends AppCompatActivity {
    Bundle parametros = new Bundle();
    FloatingActionButton btn;
    ListView lts;
    Cursor cProducto;
    DB db_producto;
    producto misProductos;

    final ArrayList<producto> alProducto=new ArrayList<producto>();
    final ArrayList<producto> alProductoCopy=new ArrayList<producto>();
    JSONArray datosJSON;
    JSONObject jsonObject;
    ObtenerDatosServidor datosServidor;
    DetectarInternet di;
    int posicion = 0;
    String respuesta = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lista_producto);

        db_producto = new DB(lista_producto.this, "", null, 1);

        btn = findViewById(R.id.btnAbrirNuevosProductos);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                parametros.putString("accion", "nuevo");
                abrirActividad(parametros);
            }
        });
        try {
            di = new DetectarInternet(getApplicationContext());
            if(di.hayConexionInternet()){
                obtenerDatosProductosServidor();
            }else {
                obtenerProducto();
            }
            subirDatos();
        } catch (Exception e) {
            mostrarMsg("Error al detectar si hay conexion"+ e.getMessage());
        }
        buscarProducto();
    }

    private void obtenerDatosProductosServidor(){
        try {
            datosServidor = new ObtenerDatosServidor();
            String data = datosServidor.execute().get();
            jsonObject = new JSONObject(data);
            datosJSON = jsonObject.getJSONArray("rows");
            MostrarProductos();
        }catch (Exception e){
            mostrarMsg("Error al obtener datos desde el servidor: "+ e.getMessage());
        }
    }
    private void MostrarProductos() {
        try {
            if (datosJSON.length()>0 ) {
                lts = findViewById(R.id.ltsProducto);

                alProducto.clear();
                alProductoCopy.clear();

                JSONObject misProductosJSONObject;
                for (int i=0; i<datosJSON.length(); i++) {
                    misProductosJSONObject = datosJSON.getJSONObject(i).getJSONObject("value");
                    misProductos = new producto(
                            misProductosJSONObject.getString("_id"),
                            misProductosJSONObject.getString("_rev"),
                            misProductosJSONObject.getString("idProducto"),
                            misProductosJSONObject.getString("codigo"),
                            misProductosJSONObject.getString("descripcion"),
                            misProductosJSONObject.getString("marca"),
                            misProductosJSONObject.getString("presentacion"),
                            misProductosJSONObject.getString("precio"),
                            misProductosJSONObject.getString("foto")
                    );
                    alProducto.add(misProductos);
                }
                adaptadorImagenes adImagenes = new adaptadorImagenes(getApplicationContext(), alProducto);
                lts.setAdapter(adImagenes);
                alProductoCopy.addAll(alProducto);

                registerForContextMenu(lts);
            } else {
                alProducto.clear();
                adaptadorImagenes adImagenes = new adaptadorImagenes(getApplicationContext(), alProducto);
                lts.setAdapter(adImagenes);
                adImagenes.notifyDataSetChanged();

                mostrarMsg("No hay datos que mostrar.");

            }
        } catch (Exception e) {
            mostrarMsg("Error al mostrar datos:" + e.getMessage());
        }
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mimenu, menu);
        try{
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            posicion = info.position;
            menu.setHeaderTitle(datosJSON.getJSONObject(posicion).getJSONObject("value").getString("codigo"));//1 es el nombre del producto
        }catch (Exception e){
            mostrarMsg("Error al mostrar el menu: "+ e.getMessage());
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        try {
            switch (item.getItemId()){
                case R.id.mnxAgregar:
                    parametros.putString("accion", "nuevo");
                    abrirActividad(parametros);
                    break;
                case R.id.mnxModificar:
                    parametros.putString("accion","modificar");
                    parametros.putString("productos", datosJSON.getJSONObject(posicion).toString());
                    abrirActividad(parametros);
                    break;
                case R.id.mnxEliminar:
                    eliminarProducto();
                    break;
            }

            return true;
        } catch (Exception e) {
            mostrarMsg("Error en menu: " + e.getMessage());
            return super.onContextItemSelected(item);
        }
    }
    private void eliminarProducto(){
        try {
            AlertDialog.Builder confirmacion = new AlertDialog.Builder(lista_producto.this);
            confirmacion.setTitle("Esta seguro de Eliminar a: ");
            confirmacion.setMessage(datosJSON.getJSONObject(posicion).getJSONObject("value").getString("idProducto"));
            confirmacion.setPositiveButton("SI", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        String respuesta = db_producto.administrar_Productos("eliminar", new String[]{ datosJSON.getJSONObject(posicion).getJSONObject("value").getString("idProducto")});
                        if (respuesta.equals("ok")) {
                            mostrarMsg("producto eliminado con exito.");
                            obtenerProducto();
                            subirDatos();
                        } else {
                            mostrarMsg("Error al eliminar producto: " + respuesta);
                        }
                    }catch (Exception e){
                        mostrarMsg("Error al eliminar Datos: "+e.getMessage());
                    }
                }
            });
            confirmacion.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            confirmacion.create().show();
        }catch (Exception e){
            mostrarMsg("Error al eliminar: "+ e.getMessage());
        }
    }

    private void subirDatos(){
        try{
            JSONObject misDatosJSONObject = new JSONObject();
            if (datosJSON.length()>0){
                for (int i=0; i<datosJSON.length(); i++) {
                    misDatosJSONObject = datosJSON.getJSONObject(i).getJSONObject("value");

                    if(misDatosJSONObject.getString("_id").equals("")&& misDatosJSONObject.getString("_rev").equals("")){
                        guardarDatosServidor(misDatosJSONObject);
                       // mostrarMsg(misDatosJSONObject.toString());
                    }
                }
                //* guardarDatosServidor(misDatosJSONObject);
            }

        }catch (Exception e){mostrarMsg("Error al sincronizar: "+e.getMessage());}
    }

    private void guardarDatosServidor(JSONObject datosProductos){
        try {
            mostrarMsg("guardarDatosServidor");
            String respuesta = "";
            datosProductos.remove("_id");
            datosProductos.remove("_rev");
            EnviarDatosServidor objGuardarDatosServidor = new EnviarDatosServidor(getApplicationContext());
            respuesta = objGuardarDatosServidor.execute(datosProductos.toString()).get();
            JSONObject respuestaJSONObject = new JSONObject(respuesta);
            mostrarMsg(respuesta);
            if (respuestaJSONObject.getBoolean("ok")) {
                mostrarMsg("Datos sincronizados los datos");
                obtenerDatosProductosServidor();
            }
            else{
                mostrarMsg("Error al sincronizar los datos");
            }
        }catch (Exception e) {

            mostrarMsg("Error en server: "+e.getMessage());
        }

    }

    private void abrirActividad(Bundle parametros){
        Intent abriVentana = new Intent(getApplicationContext(), MainActivity.class);
        abriVentana.putExtras(parametros);
        startActivity(abriVentana);
    }
    private void obtenerProducto(){
        try {
            cProducto = db_producto.consultar_Productos();
            if (cProducto.moveToFirst() ) {
                datosJSON = new JSONArray();
                do {
                    jsonObject = new JSONObject();
                    JSONObject jsonObjectValue = new JSONObject();
                    jsonObject.put("_id",cProducto.getString(0));
                    jsonObject.put("_rev",cProducto.getString(1));
                    jsonObject.put("idProducto", cProducto.getString(2));
                    jsonObject.put("codigo", cProducto.getString(3));
                    jsonObject.put("descripcion", cProducto.getString(4));
                    jsonObject.put("marca", cProducto.getString(5));
                    jsonObject.put("presentacion", cProducto.getString(6));
                    jsonObject.put("precio", cProducto.getString(7));
                    jsonObject.put("foto", cProducto.getString(8));

                    jsonObjectValue.put("value", jsonObject);
                    datosJSON.put(jsonObjectValue);
                }while(cProducto.moveToNext());
                MostrarProductos();
            } else {
                alProducto.clear();
                adaptadorImagenes adImagenes = new adaptadorImagenes(getApplicationContext(), alProducto);
                lts.setAdapter(adImagenes);
                adImagenes.notifyDataSetChanged();

                mostrarMsg("No hay productos que mostrar");
            }
        } catch (Exception e) {
            mostrarMsg("Error al obtener productos: "+ e.getMessage());
        }
    }
    private void mostrarMsg(String msg){
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    private void buscarProducto(){
        TextView tempVal;
        tempVal = findViewById(R.id.txtBuscarProducto);
        tempVal.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    alProducto.clear();
                    String valor = tempVal.getText().toString().trim().toLowerCase();
                    if( valor.length()<=0 ){
                        alProducto.addAll(alProductoCopy);
                    }else{
                        for( producto Producto : alProductoCopy ){
                            String codigo = Producto.getCodigo();
                            String descripcion = Producto.getDescripcion();
                            String marca = Producto.getMarca();
                            String presentacion = Producto.getPresentacion();
                            if( codigo.toLowerCase().trim().contains(valor) ||
                                    descripcion.toLowerCase().trim().contains(valor) ||
                                    marca.trim().contains(valor) ||
                                    presentacion.trim().toLowerCase().contains(valor) ){
                                alProducto.add(Producto);
                            }
                        }
                        adaptadorImagenes adImagenes = new adaptadorImagenes(getApplicationContext(), alProducto);
                        lts.setAdapter(adImagenes);
                    }
                }catch (Exception e){
                    mostrarMsg("Error al buscar: "+e.getMessage() );
                }
            }
            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }
}